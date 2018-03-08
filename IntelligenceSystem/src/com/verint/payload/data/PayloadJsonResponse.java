package com.verint.payload.data;

import java.util.Optional;

import org.json.JSONObject;

/**
 * A json report from PayloadSecurity. 
 * Because an error comes as a 200 status with a different json format, 
 * we had to inherit a bit awkwardly from PayloadResponse 
 * @author Assaf
 */
public class PayloadJsonResponse extends PayloadResponse
{
	private JSONObject jsonReport;

	public PayloadJsonResponse(JSONObject report)
	{
		this(0, "", report);
	}

	public PayloadJsonResponse(int responseCode, String responseValue, JSONObject report)
	{
		super(responseCode, responseValue);
		
		this.jsonReport = report;
	}
	
	public Optional<JSONObject> getReport()
	{
		return Optional.of(jsonReport);
	}
}
