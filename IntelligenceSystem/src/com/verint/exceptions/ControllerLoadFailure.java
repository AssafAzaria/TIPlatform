package com.verint.exceptions;


public class ControllerLoadFailure extends RuntimeException
{

	public ControllerLoadFailure()
	{
		super();
	}

	public ControllerLoadFailure(Throwable cause)
	{
		super(cause);
	}
	
	public ControllerLoadFailure(String message, Throwable cause)
	{
		super(message, cause);
	}

	public ControllerLoadFailure(String message)
	{
		super(message);
	}

	
	
	
}
