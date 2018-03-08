package com.verint.exceptions;

/**
 * Wrapper exception for all dao problems
 * @author Assaf Azaria
 */
public class DaoException extends RuntimeException
{
	private static final long serialVersionUID = -7422091181639083626L;

	public DaoException(String message, Throwable cause) {
		super(message, cause);
	}

	public DaoException(String message) {
		super(message);
	}

	public DaoException(Throwable cause) {
		super(cause);
	}

}
