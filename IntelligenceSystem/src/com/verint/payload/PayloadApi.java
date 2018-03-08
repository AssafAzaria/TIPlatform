package com.verint.payload;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.verint.payload.data.PayloadJsonResponse;
import com.verint.payload.data.PayloadPcapResponse;
import com.verint.payload.data.PayloadResponse;
import com.verint.utils.ErrorLogger;

/**
 * Implements payload security rest api
 * 
 * @author Assaf Azaria
 */
public class PayloadApi
{
	static final String URI_SUBMIT_FILE = "https://verint.vxstream-sandbox.com/api/submit";
	static final String URI_FILE_REPORT = "https://verint.vxstream-sandbox.com/api/result";
    static final String URI_SCAN_REPORT = "https://verint.vxstream-sandbox.com/api/scan";
	
	private static final String API_KEY = "apikey";
	private static final String SECRET = "secret";
	private static final String ENV_ID = "environmentId";
	private static final String TYPE = "type";

	private Logger logger = ErrorLogger.getInstance().getLogger();
	
	private final String apiKey; 
	private final String secret; 

	public PayloadApi()
	{
		// default api key and secret
		this("16d0f569f7dw0wko4gggk04g0", 
			 "2893ebc6e8f215a2cfe32294a6b8985579968d23d262a9e2");
//		this("24u17w2qx3tw808owc8wcgwwg",
//				"dc341a1d2caa5c4ce1a7ad19c77a5dbf3fc603813529b807");
	}
	
	public PayloadApi(String apiKey, String secret)
	{
		this.apiKey = apiKey;
		this.secret = secret;
		
		Unirest.setDefaultHeader("accept", "application/json");
	}
	
	/**
	 * Submit a file to Payload security
	 * @return The response contains the file sha 256, or an error string.
	 */
	public PayloadResponse scanFile(File fileToScan) throws PayloadException
	{
		if (fileToScan ==null || !fileToScan.canRead()) {
			throw new PayloadException("File " + fileToScan + " cannot be read");
		}
		
		Integer statusCode = -1;
		try{
			HttpResponse<JsonNode> res = Unirest.post(URI_SUBMIT_FILE)
					  .field(API_KEY, apiKey)
					  .field(SECRET, secret)
					  .field(ENV_ID, "100")
					  .field("file", fileToScan)
					  .asJson();
			
			statusCode = res.getStatus();
			return parseSimpleResponse(res.getBody());
		}
		catch(UnirestException e)
		{
			logger.severe("PAYLOAD API: -- http request failed -- " + e.getMessage());
			logger.log(Level.FINE, "-- Request failed: " + statusCode, 
					e.getCause());
			// wrap the exception and pass on to caller
			throw new PayloadException(e);
		}
	}
	
	/**
	 * Main report on a previously submitted file
	 * @param hash the file hash (sha 256)
	 * @param type json, pdf, html, xml etc
	 * @param toSave a file to save the results to
	 * @return 
	 */
	public PayloadResponse getJsonReport(String hash) 
			throws PayloadException{
		
		// add the resource to the url path
		String url = URI_FILE_REPORT + "/" + hash;

		Integer statusCode = -1;
		
		try{
			HttpResponse<JsonNode> res = Unirest.get(url)
					  .queryString(API_KEY, apiKey)
					  .queryString(SECRET, secret)
					  .queryString(ENV_ID, "100")
					  .queryString(TYPE, "json")
					  .asJson();
					  			
			statusCode = res.getStatus();
			
			JsonNode json = res.getBody();
			if (isErrorRepsonse(json))
			{
				return parseSimpleResponse(json);
			}
			
			return new PayloadJsonResponse(json.getObject());
			
		}
		catch(UnirestException e)
		{
			logger.severe("PAYLOAD API: -- http request failed -- " + e.getMessage());
			logger.log(Level.FINE, "-- Request failed: " + statusCode, 
					e.getCause());
			// wrap the exception and pass on to caller
			throw new PayloadException(e);
		}
	}

	/**
	 * Get pcap of submitted file.
	 * @param hash the file hash (sha 256)
	 * @return the pcap stream
	 */
	// It is probably better to call this only after you get the json report, 
	// to ensure the report is is ready, otherwise you'll get a tiny pcap with
	// encoded error message in it.
	public PayloadPcapResponse getPcapReport(String hash)
			throws PayloadException{
 
		// add the resource to the url path
		String url = URI_FILE_REPORT + "/" + hash;

		Integer statusCode = -1;
		try{
			HttpResponse<InputStream> res = Unirest.get(url)
					  .queryString(API_KEY, apiKey)
					  .queryString(SECRET, secret)
					  .queryString(ENV_ID, "100")
					  .queryString(TYPE, "pcap")
					  .asBinary();
					  			
			statusCode = res.getStatus();
			logger.fine("PAYLOAD API: Status code: " + statusCode);
			
			byte[] compressedPcapData = IOUtils.toByteArray(res.getBody());
			return new PayloadPcapResponse(compressedPcapData);
		}
		catch(UnirestException | IOException e)
		{
			logger.severe("PAYLOAD API: -- http request failed -- " + e.getMessage());
			logger.log(Level.FINE, "-- Request failed: " + statusCode, 
					e.getCause());
			// wrap the exception and pass on to caller
			throw new PayloadException(e);
		}
	}
	
	/**
	 * Get report on files that were not submitted by us. You'll get a small report
	 * compares to the elaborate report you get on files you've submitted.
	 * 
	 * @param hash the file hash (sha 256)
	 * @return the pcap stream
	 */
	public PayloadResponse getScanMinimalReport(String hash) throws PayloadException
	{
		// add the resource to the url path
		String url = URI_SCAN_REPORT + "/" + hash;

		Integer statusCode = -1;
		try{
			HttpResponse<JsonNode> res = Unirest.get(url)
					  .queryString(API_KEY, apiKey)
					  .queryString(SECRET, secret)
					  .queryString(ENV_ID, "100")
					  .queryString(TYPE, "json")
					  .asJson();
					  			
			statusCode = res.getStatus();
			logger.fine("Status code: " + statusCode);
			
			JsonNode json = res.getBody();
			if (isErrorRepsonse(json))
			{
				return parseSimpleResponse(json);
			}
						
			return new PayloadResponse(0, json.toString());
			
		}
		catch(UnirestException e)
		{
			logger.severe("PAYLOAD API: -- http request failed -- " + e.getMessage());
			logger.log(Level.FINE, "-- Request failed: " + statusCode, 
					e.getCause());
			// wrap the exception and pass on to caller
			throw new PayloadException(e);
		}
	}
	
	//
	// Helpers
	//
	
	// Check if the given json response is a payload error response.
	private boolean isErrorRepsonse(JsonNode json)
	{
		// format: e.g. {"response_code":-x, "response":{"sha256":""}}
		return json.getObject().has("response") && 
			   json.getObject().has("response_code");
	}
	
	// Parse a payload response that comes in case of errors, or in response
	// to scans. They use the same format for both cases.
	private PayloadResponse parseSimpleResponse(JsonNode json)
	{
		// format: e.g. {"response_code":0, "response":{"sha256":""}}
		JSONObject obj = json.getObject();
		int resCode = obj.getInt("response_code");
		JSONObject resVal = obj.getJSONObject("response");
		
		PayloadResponse res = new PayloadResponse(resCode, resVal.toString());
		
		// optional values
		res.setError(resVal.optString("error"));
		res.setSha256(resVal.optString("sha256"));
		
		return res;
	}
	
}
