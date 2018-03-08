package com.verint.main;


import com.verint.exceptions.FileSubmittionFailedException;

/**
 * A generic interface for all controllers
 * 
 * @author Assaf Azaria
 */
public interface EngineController {
	
	/** 
	 * get data on file from the relevant engine and save in repository.  
	 * 
	 * @param sample the sample
	 * @return whether the operation was completed
	 * @throws FileSubmittionFailedException on failure
	 */
	public boolean getDataOnFile(SampleFile sample) throws FileSubmittionFailedException;
	
	/**
	 * Supply relevant data on controller
	 * 
	 * @return a ControllerData object
	 */
	public ControllerData getControllerData();
	
	/**
	 * Clean resources and shutdown
	 */
	public void shutdownController();
}
