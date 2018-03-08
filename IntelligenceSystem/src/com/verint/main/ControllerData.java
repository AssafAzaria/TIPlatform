package com.verint.main;

import java.util.function.Predicate;

import org.apache.tika.mime.MediaType;

/**
 * An object used by controllers to provide data on themselves to 
 * the system
 * @author Assaf Azaria
 *
 */
public final class ControllerData
{
	// the controller type
	private final CheckType checkType;
	
	// Indicates what file types the controller supports
	private final Predicate<MediaType> isSupported;
	
	public ControllerData(CheckType type, Predicate<MediaType> isSupported)
	{
		this.checkType = type;
		this.isSupported = isSupported; 
	}
	
	public CheckType getCheckType()
	{
		return checkType;
	}
	
	public boolean isTypeSupported(MediaType type){
		return isSupported.test(type);
	}

	@Override
	public String toString()
	{
		return String.format("ControllerData [checkType=%s, isSupported=%s]", checkType, isSupported);
	}
	
	

}
