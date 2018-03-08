package com.verint.payload;

/**
 *  A wrapper for all payload api exceptions
 *  @Author Assaf Azaria
 */
public class PayloadException extends RuntimeException
{

	private static final long serialVersionUID = 1L;

	public PayloadException(String message, Throwable cause)
	{
		super(message, cause);
	}

	public PayloadException(String message)
	{
		super(message);
	}

	public PayloadException(Throwable cause)
	{
		super(cause);
	}

	

}
