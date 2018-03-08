package com.verint.payload.data;

import java.util.Optional;

import org.json.JSONObject;

/**
 * Represents a basic payload response. Can be a scan response, or 
 * error responses on report requests. 
 * 
 * @author Assaf Azarua
 */
public class PayloadResponse
{
	private int responseCode = 0;
	private String responseValue;
	
	private String sha256;
	private String error;
	
	public PayloadResponse(int responseCode, String responseValue)
	{
		this.responseCode = responseCode;
		this.responseValue = responseValue;
	}

	public boolean isError()
	{
		return responseCode != 0;
	}
	
	public int getResponseCode()
	{
		return responseCode;
	}
	
	public String getResponseValue()
	{
		return responseValue;
	}
	
	public void setError(String error)
	{
		this.error = error;
	}
	
	public void setSha256(String sha256)
	{
		this.sha256 = sha256;
	}
	
	public String getError()
	{
		return error;
	}
	
	public Optional<JSONObject> getReport(){
		return Optional.empty();
	}
	
	@Override
	public String toString()
	{
		return String.format("PayloadResponse [responseCode=%s, responseValue=%s, sha256=%s, error=%s]", responseCode,
				responseValue, sha256, error);
	}

	public String getSha256()
	{
		return sha256;
	}
	
}
