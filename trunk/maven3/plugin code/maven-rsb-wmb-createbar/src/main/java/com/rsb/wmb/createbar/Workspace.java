package com.rsb.wmb.createbar;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Workspace {

	private static final String[] suffix = {"mset","jar","outadapter" }; // removed "msgflow" as this is passed as main artifact

	private String path;

	private List<String> artifacts = new ArrayList<String>();

	public Workspace(String path) {
		this.path = path;
	}

	public List<String> findArtifacts(List<String> projects) throws IOException {
		Iterator<String> iterator = projects.iterator();
		while (iterator.hasNext()) {
			String project = this.path + File.separator + iterator.next();
			File projectDir = new File(project);
			if (!projectDir.exists())
				throw new IOException("Project folder " + project + " does not exist.");
			this.traverse(projectDir);
		}
		return this.artifacts;
	}

	private void traverse(File file) throws IOException {
		if (file.isDirectory()) {
			String[] children = file.list();
			for (int i = 0; i < children.length; i++) {
				traverse(new File(file, children[i]));
			}
		} else {
			isBrokerArtifact(file);
		}
	}

	private void isBrokerArtifact(File file) {
		String name = file.getName();
		for (int i = 0; i < suffix.length; i++) {
			if (name.endsWith(suffix[i])) {
				String subPath = file.getAbsolutePath().substring(this.path.length() + 1);
				this.artifacts.add(subPath);
			}
		}
	}

	public void deleteMetadata() {
		File ws = new File(path + File.separator + ".metadata");
		if (ws.exists()) {
			this.deleteDir(ws);
		}
	}

	public boolean deleteDir(File dir) {
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (int i = 0; i < children.length; i++) {
				boolean success = deleteDir(new File(dir, children[i]));
				if (!success) {
					return false;
				}
			}
		}
		return dir.delete();
	}

	public void copyProjectToNewWorkspace(String projectname, Workspace toWorkspace)
			throws IOException {
		File fromdir = new File(this.getPath() + File.separator + projectname);
		File todir = new File(toWorkspace.getPath() + File.separator + projectname);
		this.copyDirectory(fromdir, todir);
	}

	private void copyDirectory(File srcDir, File dstDir) throws IOException {
		//No need top copy a possible maven target folder:
		if (srcDir.isDirectory() && !srcDir.getName().equals("target")) { 
			if (!dstDir.exists()) {
				dstDir.mkdirs();
			}
			String[] children = srcDir.list();
			for (int i = 0; i < children.length; i++) {
				copyDirectory(new File(srcDir, children[i]), new File(dstDir, children[i]));
			}
		} else {
			copy(srcDir, dstDir);
		}
	}

	private void copy(File src, File dst) throws IOException {
		InputStream in = new FileInputStream(src);
		if (!dst.exists())
			dst.createNewFile();
		OutputStream out = new FileOutputStream(dst);

		// Transfer bytes from in to out
		byte[] buf = new byte[1024];
		int len;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}

	public boolean isFlowProject(String projectname) {
		File projectfile = new File(path + File.separator + projectname + File.separator
				+ ".project");
		return isProjectOfType(projectfile, "msgflowbuilder");
	}

	public boolean isJavaProject(String projectname) {
		File projectfile = new File(path + File.separator + projectname + File.separator
				+ ".project");
		return isProjectOfType(projectfile, "jcnbuilder");
	}

	private boolean isProjectOfType(File project, String identifier) {
		try {
			BufferedReader reader = new BufferedReader(new FileReader(project));
			String readstring;
			while ((readstring = reader.readLine()) != null) {
				if (readstring.indexOf(identifier) != -1) {
					reader.close();
					return true;
				}
			}
			reader.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Missing .project file");
		} catch (IOException e) {
			throw new RuntimeException("Could not read .project");
		}
		return false;
	}

	public List<String> getDependencies(String projectname) {
		List<String> dependencies = new ArrayList<String>();
		String fullname = path + File.separator + projectname + File.separator + ".project";
		try {
			BufferedReader reader = new BufferedReader(new FileReader(fullname));
			String readstring;
			int pos;
			while ((readstring = reader.readLine()) != null) {
				if ((pos = readstring.indexOf("<project>")) != -1) {
					pos+=9;
					int endpos = readstring.indexOf("</project>");
					String name = readstring.substring(pos, endpos);
					dependencies.add(name);
				}
			}
			reader.close();
		} catch (FileNotFoundException e) {
			throw new RuntimeException("Missing .project file");
		} catch (IOException e) {
			throw new RuntimeException("Could not read .project");
		}
		return dependencies;
	}

	public String getPath() {
		return path;
	}
}
