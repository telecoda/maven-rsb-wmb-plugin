package com.rsb.wmb.createbar;


/*
 * Copyright 2010 
 *
 * by Rob Baines
 * 
 * 16 July 2010
 * 
 * Mojo based plugin for automating the build/deployment of IBM Message broker artifacts
 * 
 * Goal "createbar" invokes the mqsicreatebar command
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.WriterStreamConsumer;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Goal which creates a barfile
 *
 * @goal createbar
 * @requiresProject false
 */
public class WmbCreateBarMojo
    extends AbstractMojo
{
	// define parameters for mojo
    /**
     * Name of the barfile.
     * @parameter 
     * @required
     */
	private String barfile = "";

    /**
     * Target of workspace
     * @parameter 
     * @required
     */
	private String workspaceTarget = "";

	/**
     * Project
     * @parameter 
     * @required
     */
	private String wmbProject = "";

	/**
     * Main artifact
     * @parameter 
     * @required
     */
	private String mainArtifact = "";

	/**
     * location of mqsicreatebar command
     * @parameter 
     * @required
     */
	private String mqsicreatebarLocation ="";

	/**
     * Level of verbosity in logging messages
     * @parameter 
     * @required
     */
	private boolean verboseMode =false;

	private boolean cleanmetadata = false;
    
	// constants
	final int BUFFER = 2048;
	final static String tempDirName = "tempfiles";
	
	//private ArrayList<String> messageFlows = new ArrayList<String>();
	//private String brokerXmlPath=null;
	
	// workspace variables
	private List<Project> projects = new ArrayList<Project>();
	private boolean debug = false;

	
	public void execute() 
        throws MojoFailureException
        {
		boolean allProjsFound =false;
		boolean barFileCreated=false;
		String sourceBarSuffix;
		String failureMessage="";
		
		// validate mandatory parameters and throw exception if they do not exist
		if (getMqsicreatebarlocation() == null || getMqsicreatebarlocation().length() == 0)
			throw new MojoFailureException("Missing mqsicreatebarLocation value.");
		if (getName() == null || getName().length() == 0)
			throw new MojoFailureException("Missing name value.");
		if (workspaceTarget == null || workspaceTarget.length() == 0)
			throw new MojoFailureException("Missing workspaceTarget value.");
		if (wmbProject == null || wmbProject.length() == 0)
			throw new MojoFailureException("Missing wmbproject value.");
		if (getMainArtifact() == null || getMainArtifact().length() == 0)
			throw new MojoFailureException("Missing mainArtifact value.");
		getLog().info("[Output file: " + getName() + "], [Workspace: " + workspaceTarget
				+ "], [Main Artifact: " + getMainArtifact() + "]");



		Workspace myWorkspace = new Workspace(workspaceTarget);
		String mainProject = wmbProject;
		String barFile = getName();
		// get dependent projects for main project
		List<String> depprojectnames = myWorkspace.getDependencies(mainProject);
		// combine main project with dependencies
		List<String> allProjects = new ArrayList<String>(depprojectnames);
		allProjects.add(mainProject);
		
		getLog().info("Project dependencies:" + depprojectnames);
		List<String> artifacts = null;
		// derive all project dependencies by drilling down .project files
		try {
			artifacts = myWorkspace.findArtifacts(depprojectnames);
			getLog().info("Found artefacts: " + artifacts);
			allProjsFound = true;
		} catch (IOException e) {
			getLog().error("Error finding project:"+e.getMessage());
			failureMessage = "Error finding project:"+e.getMessage();
			// save error message to variable for inclusion in failed .barfile
			allProjsFound = false;
		}
		if (getCleanmetadata()) {
			getLog().info("Deleting .metadata");
			myWorkspace.deleteMetadata();
		}
		
		// if all sourcecode has been found it is safe to execute the command to create the bar file
		if(allProjsFound)
		{
			// perform create bar file all source code found
			barFileCreated = this.executeMqsiCreateBar(myWorkspace, mainProject, depprojectnames,
				artifacts);
		}
		
		// create another bar file containing the source code?
		String tempFilesLocation = myWorkspace.getPath()+File.separator+tempDirName;

		// clear out any old files in the temp location
		getLog().info("Deleting dir:"+tempFilesLocation);
		myWorkspace.deleteDir(new File(tempFilesLocation));

		
		// if all source code found
		if(allProjsFound)
		{
			// extract bar file that has been created so source code can be
			// added
			this.unzipBar(barFile,tempFilesLocation);
			// copy source projects to src folder in temp location

			// determine filename for .bar file containing sourcecode
			if(barFileCreated)
			{
				// bar file creation was successful
				// create a barfile with all source
				sourceBarSuffix = "-withSource.bar";

			}
		
			else
			{
				sourceBarSuffix = "-withSource[FAILED].bar";

				// bar file creation failed or project had missing resources
				// delete if it already exists.
				getLog().error("Build failed so rename partially created file:"+getName());
				String renameFileName = getName().replace(".bar", "[FAILED].bar");
				File originalFile = new File(getName());
				File renameFile = new File(renameFileName);
				boolean renameSuccess = originalFile.renameTo(renameFile);
				if(renameSuccess)
				{
					getLog().info("File was renamed.");
				}
				else
				{
					getLog().error("Failed to rename file:"+getName());
				}
			}
			// copy all projects source code into same location as artifacts
			this.copyProjectsSource(allProjects,tempFilesLocation+File.separator+"src");

		
			// rezip all artifacts and source into a single new file
			String barWithSource = barFile.replace(".bar", sourceBarSuffix);
			this.zipBar(barWithSource,tempFilesLocation);
		}
			
		else
		{
			// this was a failure retrieving all source code required (dodgy
			// project refs?)
			getLog().info("Creating directory: " + tempFilesLocation);
			new File(tempFilesLocation).mkdirs();
			getLog().info("Directory created: " + tempFilesLocation);
			// create a single file in the temp work area containing the reason for failure.
			String errorFilename = tempFilesLocation+File.separator+"ErrorReason.txt";
			getLog().info("Writing failure reason to ErrorReason.txt");
			try { BufferedWriter out = new BufferedWriter(new FileWriter(errorFilename));
			
			out.write(failureMessage); out.close();
			sourceBarSuffix = "-[FAILED].bar";
			// rezip all artifacts and source into a single new file
			String barWithSource = barFile.replace(".bar", sourceBarSuffix);
			this.zipBar(barWithSource,tempFilesLocation);
			// create bar file
			} catch (IOException e) {
				getLog().error(e.getMessage());
			} 
		}
		if(!allProjsFound | !barFileCreated)
		{
			// throw build failure exception
			throw new org.apache.maven.plugin.MojoFailureException("Bar file creation failed."+getName());
		}
	}

	private boolean executeMqsiCreateBar(Workspace workspace, String mainProject,
			List<String> depProjectNames, List<String> artefacts) 
	
	
	{
		Commandline cmdline = new Commandline();
	    cmdline.setExecutable(mqsicreatebarLocation);

		(cmdline.createArg()).setValue("-data");
		(cmdline.createArg()).setValue(workspace.getPath());
		(cmdline.createArg()).setValue("-b");
		(cmdline.createArg()).setValue(getName());
		(cmdline.createArg()).setValue("-cleanBuild");
		(cmdline.createArg()).setValue("-p");
		(cmdline.createArg()).setValue(mainProject);
		for (int i = 0; i < depProjectNames.size(); i++) {
			(cmdline.createArg()).setValue(depProjectNames.get(i));
		}
		(cmdline.createArg()).setValue("-o");
		// just add main artifact
		(cmdline.createArg()).setValue(mainArtifact);
		// add other non message flow artifacts
		for (int i = 0; i < artefacts.size(); i++) {
			(cmdline.createArg()).setValue(artefacts.get(i));
		}
		
	      
	    
	BufferedWriter consoleOut = new BufferedWriter(new OutputStreamWriter(System.out));
	
	
	WriterStreamConsumer systemOut = new WriterStreamConsumer(consoleOut);
	
	getLog().info("Command to run:"+cmdline.toString());
	try
	{
	// a null is passed so we do NOT capture the systemErr output stream, otherwise this actually starts outputting details of
		// exceptions being thrown from within the IBM software such as EOFExceptions which ends up being more confusing.
	int returnCode = CommandLineUtils.executeCommandLine(cmdline, systemOut, null);
	if (returnCode != 0) {
	   // bad
		return false;
	} else {
	  // good
	}
	}
	catch(CommandLineException e)
	{
		getLog().error(e.getMessage());
		return false;
	}

		return true;
	}
	

	public boolean copyProjectsSource(List<String> projects, String tempLocation) {
			Workspace fromWorkspace = new Workspace(workspaceTarget);
			Workspace toWorkspace = new Workspace(tempLocation);
			
			for (String project : projects) {
				try
				{
				fromWorkspace.copyProjectToNewWorkspace(project, toWorkspace);
				}
				catch(Exception e)
				{
					getLog().info("Source copy failed for project:"+project);
					getLog().info(e.getMessage());
					return false;
				}
			
			}
			
			return true;
			
	}

	public boolean unzipBar(String originalBar, String tempLocation) {
		try {
			
			getLog().info("unzipping bar file:"+originalBar);
			
			BufferedOutputStream dest = null;
			BufferedInputStream is = null;
			ZipEntry entry;
			ZipFile zipfile = new ZipFile(originalBar);
			Enumeration e = zipfile.entries();

				while (e.hasMoreElements()) {
				//getLog().info("Processing:"+e.toString());
				entry = (ZipEntry) e.nextElement();
				if(verboseMode)
				{
					getLog().info("Extracting: " + entry);
					
				}
				is = new BufferedInputStream(zipfile.getInputStream(entry));
				int count;
				byte data[] = new byte[BUFFER];

				String filePath = tempLocation +File.separator+ entry.getName();
				
				if(verboseMode)
				{
					getLog().info("Extracting to: " + filePath);
					
				}
				// before creating file, create directory
				//getLog().info("File path: " + filePath);
				try{
					// if pathname witin zip file create additional directory
					// NOTE!!  "/" is deliberately added this does not work with File.separator
					// as when you run this code on windows we get "/" file separators for directories within 
					// the bar file even though windows separators are "\".
					if (filePath.contains("/"))
					{
					String directoryPath = filePath.substring(0, filePath
						.lastIndexOf("/")); // remove filename
					//getLog().info("Creating directory: " + directoryPath);
					new File(directoryPath).mkdirs();
					//getLog().info("Directory created: " + directoryPath);
					}
				}
				catch(Exception ex)
				{
					getLog().info(ex.getMessage());
				}
				File currentFile = new File(filePath);
				currentFile.createNewFile(); // create the file
				//getLog().info("Current file:"+currentFile.getAbsolutePath());
		/*
		 * // capture .cmf filenames
		 * if(currentFile.getAbsolutePath().toLowerCase().endsWith(".cmf")) { //
		 * remember message flow name for processing later
		 * this.messageFlows.add(new String(currentFile.getAbsolutePath())); } //
		 * capture broker.xml filenames
		 * if(currentFile.getAbsolutePath().toLowerCase().endsWith("broker.xml")) { //
		 * remember broker.xml for processing later
		 * this.brokerXmlPath=currentFile.getAbsolutePath(); }
		 */
				FileOutputStream fos = null;
				if (!entry.isDirectory()) {
					//getLog().info("Copying file:"+filePath);
					fos = new FileOutputStream(filePath);
					dest = new BufferedOutputStream(fos, BUFFER);
					while ((count = is.read(data, 0, BUFFER)) != -1) {
						dest.write(data, 0, count);
					}
					dest.flush();
					dest.close();

				}
				is.close();
				
				}
			zipfile.close();
		} catch (Exception e) {
			getLog().info(e.getMessage());
			getLog().info("ERROR:unzipping file "+ originalBar);
			return false;
		}
		return true;
	}
	
		
	public String getName() {
		return barfile;
	}

	public void setName(String name) {
		this.barfile = name;
	}

	public String getMainArtifact() {
		return mainArtifact;
	}

	public void setMainArtifact(String mainArtifact) {
		this.mainArtifact = mainArtifact;
	}

	public String getMqsicreatebarlocation() {
		return mqsicreatebarLocation;
	}

	public void setMqsicreatebarlocation(String mqsicreatebarLocation) {
		this.mqsicreatebarLocation = mqsicreatebarLocation;
	}

	public boolean getVerbosity() {
		return verboseMode;
	}

	public void setVerbosity(boolean verbosity) {
			this.verboseMode=verbosity;
	}
	
	public boolean getCleanmetadata() {
		return cleanmetadata;
	}

	public void setCleanmetadata(boolean cleanmetadata) {
		this.cleanmetadata = cleanmetadata;
	}

	public void zipBar(String zipFileName, String dir) {
		String rootDir = dir;
		File dirObj = new File(dir);
		if (!dirObj.isDirectory()) {
			getLog().info(dir + " is not a directory");
			return;
		}

		try {

			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(
					zipFileName));
			if(verboseMode)
			{
				getLog().info("Creating : " + zipFileName);
			}
			addDir(rootDir, dirObj, out);
			// Complete the ZIP file
			out.close();

		} catch (IOException e) {
			getLog().info(e.getMessage());
			return;
		}

	}

	public void addDir(String rootDir, File dirObj, ZipOutputStream out)
			throws IOException {
		
		
		
		File[] files = dirObj.listFiles();
		byte[] tmpBuf = new byte[1024];

		for (int i = 0; i < files.length; i++) {
			if (files[i].isDirectory()) {
				addDir(rootDir,files[i], out);
				continue;
			}

			FileInputStream in = new FileInputStream(files[i].getAbsolutePath());
			// if(verboseMode)
			// {
			// getLog().info(" Adding: " + files[i].getPath());
			// }
			String localFile = files[i].getPath();
			// remove temp dir from path
			localFile = localFile.replace(rootDir+File.separator, "");
			out.putNextEntry(new ZipEntry(localFile));

			// Transfer from the file to the ZIP file
			int len;
			while ((len = in.read(tmpBuf)) > 0) {
				out.write(tmpBuf, 0, len);
			}

			// Complete the entry
			out.closeEntry();
			in.close();
		}
	}



	protected void makePathsCanoncial() throws IOException {
		File ws = new File(workspaceTarget);
		if (ws.exists()) {
			workspaceTarget=ws.getCanonicalPath();
		} else {
			throw new IOException("Source workspace does not exist");
		}
		ws = new File(workspaceTarget);
		workspaceTarget=ws.getCanonicalPath();
	}

	


	


	public boolean getDebug() {
		return debug;
	}

	public void setDebug(boolean debug) {
		this.debug = debug;
	}

	public WmbCreateBarMojo.Project createProject() {
		WmbCreateBarMojo.Project project = new WmbCreateBarMojo.Project();
		projects.add(project);
		return project;
	}

	public List<Project> getProjects() {
		return this.projects;
	}

	public class Project {
		private String name = "";

		public Project() {
		}

		public Project(String data) {
			this.name = data;
		}

		public void addText(String data) {
			this.name = data;
		}

		public String getName() {
			return this.name;
		}
	}

}

