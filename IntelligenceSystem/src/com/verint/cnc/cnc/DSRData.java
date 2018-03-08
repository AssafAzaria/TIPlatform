package com.verint.cnc.cnc;

import java.io.Serializable;
import java.util.Map;

/**
 * Holds relevant single dsr data
 * 
 * @author Assaf Azaria
 */
public class DSRData implements Serializable{
	private static final long serialVersionUID = 1L;
	
	private static final String ROW_ID = "mgm_row_id";
	private static final String HOST_CAT_ID = "host_category_id";
	private static final String INPUT_NAME = "mgm_inputname_firststring"; 
	
	private String pcapName;
	private Map<String, String> features; 
	private int rowId; // the dsr row id in db
	private int hostCategoryId;
	private double score = -1; // the 'result' of the dsr
	
	private boolean inBlackList = false;
	private boolean inWhiteList = false;
	
	public DSRData(Map<String, String> features) {
		this.features = features;
		
		this.pcapName = features.get(INPUT_NAME);
		setRowId();
		setHostCategoryId();
	}

	public int getRowId() {
		return rowId;
	}
	
	public String getPcapName() {
		return pcapName;
	}

	public int getHostCategoryId() {
		return hostCategoryId;
	}
	
	public Map<String, String> getFeatures() {
		return features;
	}
	
	public void setScore(double score) {
		this.score = score;
	}
	
	public double getScore() {
		return score;
	}

	
	public boolean isInBlackList() {
		return inBlackList;
	}
	
	public boolean isInWhiteList() {
		return inWhiteList;
	}
	
	public void setInBlackList(boolean inBlackList) {
		this.inBlackList = inBlackList;
	}
	
	public void setInWhiteList(boolean inWhiteList) {
		this.inWhiteList = inWhiteList;
	}
	
	private void setRowId(){
		try{
			 this.rowId = Integer.parseInt(features.get(ROW_ID)); 
		}catch(NumberFormatException e){
			// shouldn't happen;
			rowId = -1;
		}
	}
	
	private void setHostCategoryId()
	{
		try{
			this.hostCategoryId = Integer.parseInt(features.get(HOST_CAT_ID));
		}catch (NumberFormatException e)
		{
			// shouldn't happen;
			hostCategoryId = -1;
		}
	}
	
//	private void setAlertType()
//	{
//		try{
//			this.alertType = Integer.parseInt(features.get(ALERT_TYPE));
//		}catch (NumberFormatException e){
//			// shouldn't happen;
//			alertType = -1;
//		}
//	}
	
	
	@Override
	public String toString() {
		return String.format("DSRData [pcapName=%s, rowId=%s, hostCategoryId=%s, score=%s]", pcapName, rowId,
				hostCategoryId, score);
	}
}
