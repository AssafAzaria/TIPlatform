package com.verint.cnc.main;

import java.io.IOException;
import java.nio.file.Path;

import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.xfer.FileSystemFile;

/** 
 * Uploading of a file over SCP to an SSH server. 
 * using sshj library. Make sure known_hosts file sits in
 * ~/.ssh/known_hosts
 * 
 * @Author Assaf Azaria
 */
public class SCPUploader {
    private String host;
	private String pathOnServer;
	private String username, password;
	private SSHClient sshClient; 
	
	public SCPUploader()
	{
		this(Config.getDDPHost(), Config.getDDPUserName(), 
			 Config.getDDPPassword(), Config.getDDPFilesPath());
	}
	
	public SCPUploader(String host, String username, String password, String pathOnServer)
	{
		this.host = host;
		this.username = username;
		this.password = password;
		this.pathOnServer = pathOnServer;
		
		// log file configuration
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "TRACE");
		System.setProperty("org.slf4j.simpleLogger.logFile", "ssh_log.log");
				
	}
	
	/**
	 * Upload a file to the 'path on server'
	 * @param src the file to upload
	 * @throws IOException
	 */
	public void uploadFile(Path src)
            throws IOException{
        // needs to be created for every upload.
		createSSHClient(); 
		
		sshClient.connect(host);
        try {
        	sshClient.authPassword(username, password);

            // could have just put this before connect()
            // Make sure JZlib is in classpath for this to work
        	sshClient.useCompression();

            sshClient.newSCPFileTransfer().upload(new FileSystemFile(src.toString()), 
            		pathOnServer);
        } finally {
        	sshClient.disconnect();
        }
    }
	
	private void createSSHClient()
	{
		// config sshj
		sshClient = new SSHClient();
		
		try {
			sshClient.loadKnownHosts();
		} catch (IOException e) {
			ErrorLogger.getInstance().getLogger().severe("CNC: loadKnownHost failed: " + 
					e.getMessage());
		}
        
		// this disables (defacto) host key check
		sshClient.addHostKeyVerifier(new PromiscuousVerifier());
	}
}