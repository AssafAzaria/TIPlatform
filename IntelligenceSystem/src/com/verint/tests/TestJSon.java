package com.verint.tests;

import org.json.JSONObject;

import com.mashape.unirest.http.JsonNode;
import com.verint.payload.data.PayloadResponse;

public class TestJSon
{

	public static void main(String[] args)
	{
		//String json = "{'response_code':0,'response':{'sha256':'6518e1e6cae4617ace4c480bf94036a05360f218a4e52652fe635673ea21085f'}}";
		String json = "{'response_code':-1,'response':{'error':'Cant find requested file.'}}";
		JsonNode node = new JsonNode(json);
		PayloadResponse res = parseSimpleResponse(node);
		
		System.out.println(res);
		
	}
	
	private static PayloadResponse parseSimpleResponse(JsonNode json)
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
