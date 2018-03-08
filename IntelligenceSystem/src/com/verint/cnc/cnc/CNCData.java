package com.verint.cnc.cnc;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import com.verint.cnc.main.CncMachine;

/**
 * Holds dsr data for a single pcap file
 * 
 * @author Assaf Azaria
 */
public class CNCData implements Serializable {
	private static final long serialVersionUID = -5516247981057303697L;
	
	private final String pcapName;
	private final List<DSRData> dsrs; 
	
	// The cnc environment
	private CncMachine cncMachine;
	
	public CNCData(String pcapName, List<DSRData> dsrs) {
		this.pcapName = pcapName;
		this.dsrs = dsrs;
	}
	
	public void addDsr(DSRData dsr)
	{
		dsrs.add(dsr);
	}
	
	public List<DSRData> getDsrs() {
		return Collections.unmodifiableList(dsrs);
	}
	
	public String getPcapName() {
		return pcapName;
	}
	
	public void setCncMachine(CncMachine cncMachine) {
		this.cncMachine = cncMachine;
	}

	public CncMachine getCncMachine() {
		return cncMachine;
	}

	@Override
	public String toString() {
		return String.format("CNCData [pcapName=%s, dsrs=%s, cncMachine=%s]", pcapName, 
				dsrs, cncMachine);
	}

	
	
	

}
