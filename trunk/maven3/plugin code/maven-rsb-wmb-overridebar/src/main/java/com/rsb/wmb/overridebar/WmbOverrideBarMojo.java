package com.rsb.wmb.overridebar;


/*
 * Copyright 2010 
 *
 * by Rob Baines
 * 
 * 16 July 2010
 * 
 * Mojo based plugin for automating the build/deployment of IBM Message broker artifacts
 * 
 * Goal "overridebar" invokes the mqsiapplybaroverride command
 */

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.WriterStreamConsumer;

/**
 * Goal which overrides bar file seetings
 * 
 * @goal overridebar
 * @requiresProject false
 */
public class WmbOverrideBarMojo
    extends AbstractMojo
{
	// define parameters for mojo
    /**
	 * Name of the input barfile.
	 * 
	 * @parameter
	 * @required
	 */
	private String[] inputBarfiles;

    /**
	 * Suffix to be appended to output file
	 * 
	 * @parameter
	 * @required
	 */
	private String outputFileSuffix = "";

    /**
	 * Name of the overrides file
	 * 
	 * @parameter
	 * @required
	 */
	private String overridesFile = "";


	
	/**
	 * location of mqsiapplybaroverride command
	 * 
	 * @parameter
	 * @required
	 */
	private String mqsiapplybaroverrideLocation ="";

	public void execute() 
        throws MojoFailureException
        {
		
		String failureMsg;
		boolean failureFlag = false;
		// validate mandatory parameters and throw exception if they do not
		// exist
		if (mqsiapplybaroverrideLocation == null || mqsiapplybaroverrideLocation.length() == 0)
			throw new MojoFailureException("Missing mqsiapplybaroverrideLocation value.");
		if (inputBarfiles == null || inputBarfiles.length == 0)
			throw new MojoFailureException("Missing inputBarfile values.");
		if (outputFileSuffix == null || outputFileSuffix.length() == 0)
			throw new MojoFailureException("Missing outputFileSuffix value.");
		if (overridesFile== null || overridesFile.length() == 0)
			throw new MojoFailureException("Missing overridesFile value.");
		getLog().info("Overridding bar files");
		for(int b=0;b<inputBarfiles.length;b++) {
				getLog().info(inputBarfiles[b]);
		}
		getLog().info("[[Outfile file suffix: " + outputFileSuffix
				+ "], [Overrides file: " + overridesFile + "]");

		for(int b=0; b<inputBarfiles.length;b++)
		{
		Commandline cmdline = new Commandline();
	    cmdline.setExecutable(mqsiapplybaroverrideLocation);

		(cmdline.createArg()).setValue("-b");
		(cmdline.createArg()).setValue(inputBarfiles[b]);
		(cmdline.createArg()).setValue("-p");
		(cmdline.createArg()).setValue(overridesFile);
		(cmdline.createArg()).setValue("-o");
		String outputFilename = inputBarfiles[b].replace(".bar", outputFileSuffix+".bar");
		(cmdline.createArg()).setValue(outputFilename);
		
	      
	    
	BufferedWriter consoleOut = new BufferedWriter(new OutputStreamWriter(System.out));
	
	
	WriterStreamConsumer systemOut = new WriterStreamConsumer(consoleOut);
	
	getLog().info("Command to run:"+cmdline.toString());
	try
	{
	// a null is passed so we do NOT capture the systemErr output stream,
	// otherwise this actually starts outputting details of
		// exceptions being thrown from within the IBM software such as
		// EOFExceptions which ends up being more confusing.
	int returnCode = CommandLineUtils.executeCommandLine(cmdline, systemOut, null);
	if (returnCode != 0) {
	   // bad
		failureMsg =("Overrides failed. Return code:"+returnCode);
		getLog().error(failureMsg);
		failureFlag=true;

	}
	}
	catch(CommandLineException e)
	{
		failureMsg =("Failure executing mqsiapplybaroverride command:"+e.getMessage());
		
		getLog().error(failureMsg);
		failureFlag=true;
	}
		}
	
	// at end of loop throw exception if at least one override failed
	if(failureFlag)
	{
		throw new org.apache.maven.plugin.MojoFailureException(
		"Not all bar files overridden successfully.");
	}
	

        }
}

