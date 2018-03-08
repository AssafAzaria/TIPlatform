package com.verint.main;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a running context in the system
 * 
 * @author Assaf Azaria
 */
// TODO: more context info
public class Context {
	private final UUID contextId;
	private String description = "--undefined--";
	
	// Some files share a context, e.g pcap that arose from submitting
	// an executable. 
	private AtomicInteger runningStep = new AtomicInteger(1);
	
	public Context() {
		this.contextId = UUID.randomUUID();
	}

	public Context(UUID contextId, String description) {
		this.contextId = contextId;
		this.description = description;
	}
	
	public UUID getContextId() {
		return contextId;
	}
	
	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}
	
	public void incRunningStep()
	{
		runningStep.incrementAndGet();
	}
	
	public int getRunningStep() {
		return runningStep.get();
	}
	
	@Override
	public String toString() {
		return String.format("Context [contextId=%s, description=%s]", contextId, description);
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((contextId == null) ? 0 : contextId.hashCode());
		result = prime * result + runningStep.get();
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Context))
			return false;
		Context other = (Context) obj;
		if (contextId == null) {
			if (other.contextId != null)
				return false;
		} else if (!contextId.equals(other.contextId))
			return false;
		if (!runningStep.equals(other.runningStep))
			return false;
		return true;
	}
	
	

}
