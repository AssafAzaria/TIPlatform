package com.verint.cnc.cnc;

import java.rmi.Remote;
import java.rmi.RemoteException;

import com.verint.exceptions.FileSubmittionFailedException;

/**
 * Get cnc data remotely
 * 
 * @author Assaf Azaria
 */
public interface CNCRemote extends Remote {
	
	// Get cnc data for the given pcap
	public CNCData getCNCData(String pcapName) throws RemoteException, 
		FileSubmittionFailedException;
}
