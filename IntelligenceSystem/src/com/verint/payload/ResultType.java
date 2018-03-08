package com.verint.payload;

public enum ResultType {
	PDF("pdf", true), 
	XML_GZ("xml", true),
	HTML_GZ("html", true),
	JSON("json", false),
	PCAP("pcap", true);
	
	private String type;
	private boolean isBinary;
	
	private ResultType(String type, boolean isBinary) {
		this.type = type;
		this.isBinary = isBinary;
	}
	
	public String getType() {
		return type;
	}
	
	@Override
	public String toString() {
		return type;
	}
	
	public boolean isBinary() {
		return isBinary;
	}
	
}
