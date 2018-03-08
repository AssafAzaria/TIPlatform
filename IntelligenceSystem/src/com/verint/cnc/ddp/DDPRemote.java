package com.verint.cnc.ddp;

import java.rmi.Remote;
import java.rmi.RemoteException;

import com.verint.exceptions.FileSubmittionFailedException;

/**
 * A service that exposes when a file has been transferred by DDP
 * @author Assaf Azaria
 */
public interface DDPRemote extends Remote
{	
	public boolean isFileTransferred(String fileName) throws RemoteException, 
		FileSubmittionFailedException;
}
