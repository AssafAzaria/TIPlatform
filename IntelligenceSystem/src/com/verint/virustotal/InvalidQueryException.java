package com.verint.virustotal;

public class InvalidQueryException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public InvalidQueryException() {
	}

	public InvalidQueryException(String message) {
		super(message);
	}

	public InvalidQueryException(Throwable cause) {
		super(cause);
	}

	public InvalidQueryException(String message, Throwable cause) {
		super(message, cause);
	}

	public InvalidQueryException(String message, Throwable cause, boolean enableSuppression,
			boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

}
