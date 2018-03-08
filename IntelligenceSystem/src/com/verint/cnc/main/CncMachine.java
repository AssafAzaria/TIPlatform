package com.verint.cnc.main;

import com.verint.cnc.cnc.CNCRemote;

/**
 * Just a holder for cnc machine details.
 * Currently - host and environment.
 * 
 * @author Assaf Azaria
 */
public class CncMachine {
	private final String hostName;
	private final int environment;
	private transient CNCRemote remoteStub;
	
	public CncMachine(String hostName, int environment) {
		this.hostName = hostName;
		this.environment = environment;
	}
	
	public String getHostName() {
		return hostName;
	}
	
	public int getEnvironment() {
		return environment;
	}
	
	public void setRemoteStub(CNCRemote stub)
	{
		this.remoteStub = stub;
	}
	
	public CNCRemote getRemoteStub() {
		return remoteStub;
	}
	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + environment;
		result = prime * result + ((hostName == null) ? 0 : hostName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof CncMachine))
			return false;
		CncMachine other = (CncMachine) obj;
		if (environment != other.environment)
			return false;
		if (hostName == null) {
			if (other.hostName != null)
				return false;
		} else if (!hostName.equals(other.hostName))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return String.format("CncMachine [hostName=%s, environment=%s]", hostName, environment);
	}
	
	

}
