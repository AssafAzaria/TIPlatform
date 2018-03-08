package com.verint.moloch;

import java.nio.file.Path;
import java.time.Instant;

public class PcapData {

	// The actual file
	private Path pcapFile;
	
	// the time it was submitted to Moloch
	private Instant startTime = Instant.now().minusSeconds(1);

	// the time submittion to Moloch finished
	private Instant endTime = Instant.now();

	public PcapData(Path pcapFile) {
		this.pcapFile = pcapFile;
	}
	
	public void setStartTime(Instant startTime) {
		this.startTime = startTime;
	}
	
	public Instant getStartTime() {
		return startTime;
	}
	
	public long getStartTimeAsEpoch()
	{
		return startTime.getEpochSecond();
	}
	
	
	public void setEndTime(Instant endTime) {
		this.endTime = endTime;
	}
	
	public Instant getEndTime() {
		return endTime;
	}
	
	public long getEndTimeAsEpoch()
	{
		return endTime.getEpochSecond();
	}
	
	public Path getPcapFile() {
		return pcapFile;
	}
	
	@Override
	public String toString() {
		return String.format("PcapData [pcapFile=%s, startTime=%s, endTime=%s]", pcapFile, startTime, endTime);
	}

	public Path getAbsoluteFilePath() {
		return pcapFile.toAbsolutePath();
	}
	
	
	public String getUrlParamsSuffix()
	{
		return "startTime=" + getStartTimeAsEpoch() + "&" +
			   "endTime=" + getEndTimeAsEpoch();
	}
	

}
