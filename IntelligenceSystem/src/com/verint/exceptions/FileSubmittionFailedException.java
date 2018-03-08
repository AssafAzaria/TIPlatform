package com.verint.exceptions;

import java.nio.file.Path;

public class FileSubmittionFailedException extends RuntimeException
{
	private static final long serialVersionUID = 1L;
	
	private Path file;
	private FailReason reason;
	
	
	public FileSubmittionFailedException(Path file, FailReason reason, 
			String message, Throwable cause) {
		super(message, cause);
		this.reason = reason;
		this.file = file;
	}
	
	public FileSubmittionFailedException(Path file, FailReason reason, 
			String message) {
		super(message);
		
		this.reason = reason;
		this.file = file;
		
	}
	
	public FileSubmittionFailedException(String message) {
		super(message);
	}
	
	public FileSubmittionFailedException(String message, Throwable cause) {
		super(message, cause);
	}
	
	
	
	public Path getFileName() {
		return file;
	}
	
	public FailReason getReason() {
		return reason;
	}
	
	

}
