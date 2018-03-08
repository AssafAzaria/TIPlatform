package com.verint.virustotal;

import java.util.List;

public class VTResponse {
	private List<String> hashes;
	private String nextPage;
	
	public VTResponse(List<String> hashes, String nextPage) {
		super();
		this.hashes = hashes;
		this.nextPage = nextPage;
	}
	
	public List<String> getHashes() {
		return hashes;
	}
	public void setHashes(List<String> hashes) {
		this.hashes = hashes;
	}
	public String getNextPage() {
		return nextPage;
	}
	public void setNextPage(String nextPage) {
		this.nextPage = nextPage;
	}

	public boolean hasNextPage()
	{
		return nextPage != null;
	}
	
	public boolean hasHashes()
	{
		return hashes.size() > 0;
	}
	
	@Override
	public String toString() {
		return String.format("VTResponse [hashes=%s, nextPage=%s]", hashes, nextPage);
	}
	
	

}
