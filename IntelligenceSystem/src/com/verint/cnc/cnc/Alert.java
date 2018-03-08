package com.verint.cnc.cnc;

/**
 * Represents a cnc alert
 * @author Assaf Azaria
 */
public class Alert {
	public static final int TYPE_BEHAVIORAL = 1;
	public static final int TYPE_REPUTATIONAL = 3;
	
	private int type = -1;
	private double score = -1;
	private String hostName = "";
	private String serverIp = "";
	private String hideMask = "";
	
	public Alert(int type, double score) {
		this.type = type;
		this.score = score;
	}
	
	public Alert(int type, double score, String hostName, 
				 String serverIp, String hideMask) {
		this.type = type;
		this.score = score;
		this.hostName = hostName;
		this.serverIp = serverIp;
		this.hideMask = hideMask;
	}
	
	public int getType() {
		return type;
	}
	
	public double getScore() {
		return score;
	}

	public String getServerIp() {
		return serverIp;
	}
	
	public String getHostName() {
		return hostName;
	}
	
	public String getHideMask() {
		return hideMask;
	}

	@Override
	public String toString() {
		return String.format("Alert [type=%s, score=%s, hostName=%s, serverIp=%s]", type, score, hostName, serverIp);
	}
	
	

	
	
}
