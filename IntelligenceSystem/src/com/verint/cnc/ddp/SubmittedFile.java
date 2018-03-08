package com.verint.cnc.ddp;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Represents a file that was submitted to DDP
 * @author Assaf Azaria
 */
public class SubmittedFile {
	private Path file;
	private State state = State.UNKNOWN;
	
	// The exact time the state was changed
	private Instant timeStamp = Instant.now();
		
	public SubmittedFile(Path file) {
		this.file = file;
		
	}
//	public SubmittedFile(Path file, String hash) {
//		this.file = file;
//		this.hash = hash;
//	}
	
	public State getState() {
		return state;
	}
	
	public void setState(State state) {
		this.state = state;
	}
	
	public Path getFile() {
		return file;
	}
	
	public Instant getTimeStamp() {
		return timeStamp;
	}
	
	public void setTimeStamp(Instant timeStamp) {
		this.timeStamp = timeStamp;
	}

	@Override
	public String toString() {
		return String.format("SubmittedFile [file=%s, state=%s]", file, state);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((file == null) ? 0 : file.hashCode());
		result = prime * result + ((state == null) ? 0 : state.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof SubmittedFile))
			return false;
		SubmittedFile other = (SubmittedFile) obj;
		if (file == null) {
			if (other.file != null)
				return false;
		} else if (!file.equals(other.file))
			return false;
		if (state != other.state)
			return false;
		return true;
	}
	
	

}
