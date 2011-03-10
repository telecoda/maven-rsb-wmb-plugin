package com.rsb.wmb.deploybar;

/*
 * Copyright 2010 
 *
 * by Rob Baines
 * 
 * 16 July 2010
 * 
 * Mojo based plugin for automating the build/deployment of IBM Message broker artifacts
 * 
 * Goal "deploybar" invokes the mqsideploy command
 */

import java.io.IOException;
import java.util.Enumeration;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;

import com.ibm.broker.config.proxy.BrokerProxy;
import com.ibm.broker.config.proxy.CompletionCodeType;
import com.ibm.broker.config.proxy.ConfigManagerConnectionParameters;
import com.ibm.broker.config.proxy.ConfigManagerProxy;
import com.ibm.broker.config.proxy.ConfigManagerProxyException;
import com.ibm.broker.config.proxy.DeployResult;
import com.ibm.broker.config.proxy.ExecutionGroupProxy;
import com.ibm.broker.config.proxy.LogEntry;
import com.ibm.broker.config.proxy.MQConfigManagerConnectionParameters;
import com.ibm.broker.config.proxy.TopologyProxy;

/**
 * Goal which deploys a barfile
 *
 * @goal deploybar
 * @requiresProject false
 */
public class WmbDeployBarMojo extends AbstractMojo {
	// define parameters for mojo
	/**
	 * List of barfiles to deploy
	 * @parameter 
	 * @required
	 */
	private String[] barfiles;

	/**
	 * hostname of broker server
	 * @parameter 
	 * @required
	 */
	private String hostname = "";

	/**
	 * port
	 * @parameter 
	 * @required
	 */
	private int port = 0;

	/**
	 * Queue manager
	 * @parameter 
	 * @required
	 */
	private String queueManager = "";

	/**
	 * Broker name
	 * @parameter 
	 * @required
	 */
	private String brokerName = "";

	/**
	 * execution group
	 * @parameter 
	 * @required
	 */
	private String executionGroup = "";

	/**
	 * clear before deploy
	 * @parameter 
	 * @optional
	 */
	private boolean clearBeforeDeploy = true;

	/**
	 * deployment timeout
	 * @parameter 
	 * @optional
	 */
	private long deploymentTimeout = 10000;

	

	public void execute() throws MojoFailureException {

		boolean failureFlag = false;

		// validate mandatory parameters and throw exception if they do not exist
		if (barfiles == null || barfiles.length == 0)
			throw new MojoFailureException("Missing barfile names.");
		if (hostname == null || hostname.length() == 0)
			throw new MojoFailureException("Missing hostname value.");
		if (port == 0)
			throw new MojoFailureException("Missing port value.");
		if (queueManager == null || queueManager.length() == 0)
			throw new MojoFailureException("Missing queueManager value.");
		if (brokerName == null || brokerName.length() == 0)
			throw new MojoFailureException("Missing brokerName value.");
		if (executionGroup == null || executionGroup.length() == 0)
			throw new MojoFailureException("Missing executionGroup value.");

		getLog().info("Deployment of files:");
		for (int b = 0; b < barfiles.length; b++) {
			getLog().info(barfiles[b]);
		}
		getLog().info(
				"[host: " + hostname + "]" + " [QueueManager: " + queueManager
						+ ":" + port + " [Broker: " + brokerName + "]"
						+ " [ExecutionGroup: " + executionGroup + "]"
						+ " [DeploymentTimeout: " + deploymentTimeout + "]"
						+ " [Cleardown before deployment: " + clearBeforeDeploy
						+ "]");

		// Instantiate an object that describes the connection
		// characteristics to the Configuration Manager.
		ConfigManagerConnectionParameters cmcp = new MQConfigManagerConnectionParameters(
				hostname, port, queueManager);
		ConfigManagerProxy cmp = null;

		String failureMsg;

		try {

			// Start communication with the Configuration Manager
			getLog().info(
					"Connecting to Configuration Manager running on "
							+ queueManager + " at " + hostname + ":" + port
							+ "...");
			cmp = ConfigManagerProxy.getInstance(cmcp);

			// Has the Configuration Manager responded to the connection attempt?
			if (!cmp.hasBeenUpdatedByConfigManager(true)) {
				// The application timed out while waiting for a response from the
				// Configuration Manager. When it finally becomes available,
				// hasBeenUpdatedByConfigurationManager()
				// will return true. This application won't wait for that though-
				// it will just exit now.
				failureMsg = "Configuration Manager is not responding.";
				getLog().error(failureMsg);
				throw new org.apache.maven.plugin.MojoFailureException(
						failureMsg);
			} else {
				// Get the list of brokers defined in that CM's domain
				getLog().info("Getting domain information...");
				TopologyProxy topology = cmp.getTopology();

				// Find the broker with the given name
				getLog().info("Discovering broker '" + brokerName + "'...");
				BrokerProxy broker = topology.getBrokerByName(brokerName);

				// If the broker exists, find the execution group with the given name
				if (broker == null) {
					failureMsg = "Broker not found";
					getLog().error(failureMsg);
					throw new org.apache.maven.plugin.MojoFailureException(
							failureMsg);
				} else {
					getLog().info(
							"Discovering execution group '" + executionGroup
									+ "'...");
					ExecutionGroupProxy eg = broker
							.getExecutionGroupByName(executionGroup);

					// If the execution group exists, deploy to it.
					if (eg == null) {
						failureMsg = "Execution group not found";
						getLog().error(failureMsg);
						throw new org.apache.maven.plugin.MojoFailureException(
								failureMsg);
					} else {

						//                    	// clear execution group prior to deployment
						//                    	if(clearBeforeDeploy)
						//                    	{
						//                    		getLog().info("Clearing execution group prior to deployment");
						//                    	try{
						//                    		getLog().info("Retrieving a list of current deployed objects");
						//                    		Enumeration deployedObjectsEnum = eg.getDeployedObjects();
						//                    		
						//                    		while (deployedObjectsEnum.hasMoreElements())
						//                    		{
						//                    			
						//                    			DeployedObject deployedObject = (DeployedObject)deployedObjectsEnum.nextElement();
						//                    			
						//                    			String objectToDelete[] = new String[1];
						//                    			objectToDelete[0]= deployedObject.getName();
						//                    			getLog().info("Undeploying:"+objectToDelete[0]);
						//                    			eg.deleteDeployedObjectsByName(objectToDelete,deploymentTimeout);
						//                    			
						//                    		}
						//                    	}
						//                    	catch(Exception e)
						//                    	{
						//                    		failureMsg ="Failure undeploying object."+e.getMessage();
						//                    		getLog().error(failureMsg);
						//                            throw new org.apache.maven.plugin.MojoFailureException(failureMsg);
						//                    	}
						//                    	}
						// Deploy the BAR file and display the result
						for (int b = 0; b < barfiles.length; b++) {
							System.out.println("Deploying " + barfiles[b]
									+ "...");

							try {
								// clear execution group for first barfile if set
								DeployResult deployResult;
								if (clearBeforeDeploy && b == 0) {
									// clear before deployment
									deployResult = eg.deploy(barfiles[b], // location of BAR
											false, // incremental, i.e. don't empty the execution group first
											deploymentTimeout); // wait 10s for broker response

								} else {
									// clear before deployment
									deployResult = eg.deploy(barfiles[b], // location of BAR
											true, // incremental, i.e. don't empty the execution group first
											deploymentTimeout); // wait 10s for broker response

								}
								if (deployResult.getCompletionCode() == CompletionCodeType.failure) {

									getLog().error(
											"Deployment of " + barfiles[b]
													+ " has failed.");
									Enumeration<LogEntry> logEntries = deployResult
											.getLogEntries();
									while (logEntries.hasMoreElements()) {
										LogEntry entry = logEntries
												.nextElement();
										getLog().error(entry.getDetail());
									}
									failureFlag = true;
								} else {
									getLog()
											.info(
													"Deployment of "
															+ barfiles[b]
															+ " status:"
															+ deployResult
																	.getCompletionCode());

								}
								// You may like to improve this application by querying
								// the deployResult for more information, particularly if
								// deployResult.getCompletionCode() == CompletionCodeType.failure.

							} catch (IOException ioEx) {
								// e.g. if BAR file doesn't exist
								failureMsg = "Bar file doesn't exist."
										+ barfiles[b] + "." + ioEx.getMessage();

								getLog().error(failureMsg);
								failureFlag = true;
							}
						}

						// if any failures throw Failure exception
						if (failureFlag) {
							throw new org.apache.maven.plugin.MojoFailureException(
									"Not all bar files deployed successfully.");
						}
					}
				}
			}
		} catch (ConfigManagerProxyException cmpEx) {
			failureMsg = "Barfiles deployment failed." + cmpEx.getMessage();
			getLog().error(failureMsg);
			throw new org.apache.maven.plugin.MojoFailureException(failureMsg);
		} finally {
			if (cmp != null) {
				cmp.disconnect();
			}
		}

	}

}
