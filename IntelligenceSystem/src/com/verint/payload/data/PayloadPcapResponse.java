package com.verint.payload.data;

import java.nio.file.Path;

/**
 * Pcap response from payload security
 * 
 * @author Assaf Azaria
 */
public class PayloadPcapResponse 
{
	private byte[] compressedPcap;
	private Path pcapPath;
	
	public PayloadPcapResponse(byte[] pcap)
	{
		this.compressedPcap = pcap;
	}
	
	public byte[] getCompressedPcap()
	{
		return compressedPcap;
	}
	
	public void setPcapPath(Path pcapPath)
	{
		this.pcapPath = pcapPath;
	}
	
	public Path getPcapPath()
	{
		return pcapPath;
	}

	@Override
	public String toString()
	{
		return String.format("PayloadPcapResponse [pcap length=%s, pcapPath=%s]", 
				compressedPcap.length, pcapPath);
	}
	
	
	
	
	
	
	
	
	
	
}
