package com.verint.main;

import java.nio.file.Path;
import java.util.Optional;

import org.json.JSONObject;

/**
 * A generic response object used by asynchronous controllers
 * 
 * @author Assaf Azaria
 */
public class Response
{
	private SampleFile sample;
	private CheckType checkType;
	private JSONObject report;
	private Path pcap;
	
	public Response(SampleFile sample, CheckType type, JSONObject json)
	{
		this(sample, type, json, null);
	}
	
	public Response(SampleFile sample, CheckType type, JSONObject json, Path pcap)
	{
		this.sample = sample;
		this.checkType = type;
		this.report = json;
		this.pcap = pcap;
	}
	
	public SampleFile getSample()
	{
		return sample;
	}
	
	public CheckType getCheckType()
	{
		return checkType;
	}
	
	public Optional<Path> getPcap()
	{
		return Optional.ofNullable(pcap);
	}
	
	public Optional<JSONObject> getReport()
	{
		return Optional.ofNullable(report);
	}
	
	@Override
	public String toString()
	{
		return String.format("Response [sample=%s, report=%s, pcap=%s]", 
				sample.getPath(), report != null, pcap != null);
	}
	
	

}
