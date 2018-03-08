package com.verint.utils;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * Payload json reports are highly un-normalized for Elastic. Here we try to
 * normalize them. 
 * 
 * @author Assaf Azaria
 */
// This code is quite ugly, but I had no time to do it properly, since I
// was surprised to know that I'm no longer working here in a couple of weeks. 
public class PayloadJsonNormalizer
{
	static JSONObject dummy = new JSONObject("{\"_\":\"_\"}");

	// Get rid of elements in payload response that we do not need.
	public static void reduceReportToOurNeeds(JSONObject report) 
	{
		JSONObject analysis = report.getJSONObject("analysis");
		
		removeGeneralElements(analysis.getJSONObject("general"));
		removeRuntimeElements(analysis.getJSONObject("runtime"));
		analysis.remove("hybridanalysis");
		removeFinalElements(analysis.getJSONObject("final"));
	}

	private static void removeGeneralElements(JSONObject general)
	{
		// General
		general.remove("banner");
		general.remove("appserver");
		general.remove("controller");

		general.remove("winver_name");
		general.remove("winver_edition");
		general.remove("winver_servicepack");
		general.remove("winver_version");
		general.remove("winver_bitness");
		general.remove("winver_username");
		general.remove("winver_spoofedusername");

		general.remove("appinfos");
		general.remove("isdelayedavscan");
		general.remove("icon");

		// static
		JSONObject _static = general.getJSONObject("static");
		_static.remove("resources");

		JSONObject staticDates = _static.optJSONObject("dates");
		if (staticDates != null) {
			staticDates.remove("date_unix");
			staticDates.remove("date_year");
			staticDates.remove("dos_date_unix");
			staticDates.remove("dos_date_utc");
			staticDates.remove("dos_date_year");
		}

		JSONObject vt = general.optJSONObject("virustotal");
		if (vt != null) {
			vt.remove("sha256");
			removeElementsFromArray(vt, "result", 
					obj -> obj.getString("isvirus").equals("false"));
		}
	}

	private static void removeRuntimeElements(JSONObject runtime)
	{
		JSONObject arpRequest = runtime.optJSONObject("network").optJSONObject("arprequests");
		if (arpRequest != null){
			operateOnArrayOrObject(arpRequest, "request", t -> removeRequests(t));
		}
		
		JSONObject targets = runtime.optJSONObject("targets");
		if (targets != null){
			operateOnArrayOrObject(targets, "target", t -> removeTarget(t));
		}
		
		JSONObject dropped = runtime.optJSONObject("dropped");
		if (dropped != null) dropped.remove("file");
	}
	
	private static void removeFinalElements(JSONObject _final)
	{
		_final.remove("imageprocessing");
		_final.remove("strings");
		_final.remove("sysmon");
		_final.getJSONObject("multiscan").remove("origin_type");
		_final.remove("multiscan_extended");
		
		JSONObject signatures = _final.getJSONObject("signatures");
		if (signatures != null){
			operateOnArrayOrObject(signatures, "category", t->removeCategory(t));
		}
		
		_final.remove("signatures_chronology");
		JSONObject confidence = _final.getJSONObject("confidence");
		confidence.remove("threatsigimpact");
		confidence.remove("theoreticalmaxthreatsigimpact");
		confidence.remove("theoreticalmaxthreatsigimpact_practical");
		
		JSONObject busThreats = _final.optJSONObject("business_threats");
		if (busThreats != null) {
			operateOnArrayOrObject(busThreats, "threat", t-> removeThreats(t));
		}
	}
	
	// Trying to generalize some of this mess
	private static void operateOnArrayOrObject(JSONObject main, String key, 
			Consumer<JSONObject> func)
	{
		// try array first
		JSONArray arr = main.optJSONArray(key);
		if (arr != null){
			// use iterators for performance
			for (Iterator<Object> iter = arr.iterator(); iter.hasNext();){
				func.accept((JSONObject)iter.next());
			}
		}else{
			// it's an object
			func.accept(main.optJSONObject(key));
		}
	}
	
	// 
	// Remove functions
	// 
	private static void removeSignature(JSONObject sig)
	{
		if (sig == null) return;
		
		sig.remove("identifier");
		sig.remove("type");
	}
	
	private static void removeThreats(JSONObject threat)
	{
		if (threat == null) return;
		
		threat.remove("order");
		threat.remove("reason");
	}
	
	private static void removeTarget(JSONObject target)
	{
		if (target == null) return;
		
		target.remove("ospath");
		target.remove("cputick");
		target.remove("sha1");
		target.remove("sha512");
		target.remove("environment");
		target.remove("apicalls");
		target.remove("registry");
		target.remove("modules");
		target.remove("fileaccesses");
		
		JSONObject runSig = target.optJSONObject("runtime_signatures");
		if (runSig != null){
			JSONArray sig = runSig.optJSONArray("signature");
			if (sig != null) {
				Iterator<Object> iter = sig.iterator();
				while(iter.hasNext()){
					((JSONObject)iter.next()).remove("type");
				}
			}
		}
	}
	
	private static void removeCategory(JSONObject category)
	{
		if (category == null) return;
		category.remove("order");
		category.remove("count");
		
		operateOnArrayOrObject(category, "signature", t-> removeSignature(t));
	}
	
	private static void removeRequests(JSONObject request)
	{
		if (request == null) return;
		
		request.remove("hw_type");
		request.remove("prot_type");
		request.remove("op_type");
	}
	
	// Remove results from Virus Total, that are not viruses...
	private static void removeElementsFromArray(JSONObject root, String arrName, Predicate<JSONObject> toRemove)
	{
		JSONArray arr = root.optJSONArray(arrName);
		if (arr != null)
			for (Iterator<Object> vals = arr.iterator(); vals.hasNext();) {
				if (toRemove.test((JSONObject)vals.next())) {
					vals.remove();
				}
			}
	}


	
	
	// Trying to normalize the payload response report to have a consistent 
	// strucutre. This code is not the prettiest, but I had not time to do 
	// it properly, because I'm leaving here in couple of weeks
	public static void normalize(JSONObject report)
	{
		normalizeJsonPart("verinfo", report, "analysis", "general", "static");
		normalizeJsonPart("yarahits", report, "analysis", "general", "static");
		normalizeJsonPart("exports", report, "analysis", "general", "static");
		normalizeJsonPart("certificate", report, "analysis", "general");
		
		normalizeJsonPart("tls_callbacks", report, "analysis", "general", "static");
		normalizeJsonPart("targets", report, "analysis", "runtime");
		normalizeJsonPart("domains", report, "analysis", "runtime", "network");
		normalizeJsonPart("hosts", report, "analysis", "runtime", "network");
		normalizeJsonPart("suricata_alerts", report, "analysis", "runtime", "network");
		normalizeJsonPart("httprequests", report, "analysis", "runtime", "network");
		normalizeJsonPart("portinfo", report, "analysis", "runtime", "network");
		
		normalizeJsonPart("warnings", report, "analysis", "final");
		normalizeJsonPart("yarascanner", report, "analysis", "final");
		normalizeJsonPart("engines", report, "analysis", "final", "multiscan");
		normalizeJsonPart("business_threats", report, "analysis", "final");
		
		// TODO: unite?
		normalizeJsonArray("target", "mutants", report, "analysis", "runtime", "targets");
		normalizeJsonArray("target", "createdfiles", report, "analysis", "runtime", "targets");
		normalizeJsonArray("target", "handles", report, "analysis", "runtime", "targets");
		normalizeJsonArray("target", "hooks", report, "analysis", "runtime", "targets");
		normalizeJsonArray("target", "runtime_signatures", report, "analysis", "runtime", "targets");
		normalizeJsonArray("target", "network", report, "analysis", "runtime", "targets");
		
		normalizeJsonArray("host", "associated_domains", report, "analysis", "runtime", "network", "hosts");
		normalizeJsonArray("host", "associated_urls", report, "analysis", "runtime", "network", "hosts");
		normalizeJsonArray("host", "associated_runtime", report, "analysis", "runtime", "network", "hosts");
		normalizeJsonArray("host", "associated_sha256s", report, "analysis", "runtime", "network", "hosts");
		
		straightenArrayToIncludeOnlyObjects("domain", report, "db", "analysis", "runtime", "network", "domains");
		
		convertArrayFieldToText("target", "parentuid", report, "analysis", "runtime", "targets");
	}

	// add a dummy json object instead of the empty string if it is there.
	private static void normalizeJsonPart(String element, JSONObject report, String... path)
	{
		try {
			// go to the element
			JSONObject temp = report;
			for (String key : path) {
				temp = temp.getJSONObject(key);
				if (temp == null)
					return;
			}

			// if it is null - put a dummy object
			if (temp.optJSONObject(element) == null) {
				temp.putOpt(element, dummy);
			}
		} catch (JSONException e) {
			ErrorLogger.getInstance().getLogger().fine(e.getMessage());
			return;
		}
	}

	// "fix" an array to include only JSONObject elements.
	private static void straightenArrayToIncludeOnlyObjects(String arrKey, JSONObject report, String keyToInsert, String... path)
	{
		try {
			// find the element
			JSONObject temp = report;
			for (String key : path) {
				temp = temp.getJSONObject(key);
				if (temp == null)
					return;
			}

		JSONArray arr = temp.optJSONArray(arrKey);
		if (arr == null) {
			// maybe it's just a string:
			String str = temp.optString(arrKey);
			if (str != null && !str.equals("")) {
				JSONObject obj = new JSONObject();
				obj.put(keyToInsert, str);
				temp.put(arrKey, obj);
			}
			return;
		}

		List<String> toAdd = new ArrayList<>();
		
		// change all strings to objects
		for (Iterator<Object> iter = arr.iterator(); iter.hasNext();) {
			Object obj = iter.next();
			if (obj instanceof String) {
				iter.remove();
				toAdd.add((String)obj);
			}	
		}
		fixElements(arr, keyToInsert, toAdd);
		}catch (Exception e) {
			ErrorLogger.getInstance().getLogger().fine(e.getMessage());
			return;
		}
	}
	private static void fixElements(JSONArray arr, String keyToInsert, List<String> elements)
	{
		elements.forEach(str -> {
			JSONObject toInsert = new JSONObject();
			toInsert.put(keyToInsert, str);
			arr.put(toInsert);
		});
	}
	
	// 
	private static void convertArrayFieldToText(String arrKey, String keyToChange, JSONObject report, String... path) {
		try {

		JSONObject temp = report;
		
		for (String key : path) {
			temp = temp.getJSONObject(key);
			if (temp == null) {
				return;
			}
		}
		
		JSONArray arr = temp.optJSONArray(arrKey);
		if (arr == null) {
			// sometimes it's just an object
			JSONObject obj = temp.optJSONObject(arrKey);
			if (obj == null) return;
			changeKeyToString(obj, keyToChange);
			return;
		}
			
		for (int i = 0; i < arr.length(); i++) {
			JSONObject current = arr.getJSONObject(i);
			changeKeyToString(current, keyToChange);
		}

	} catch (JSONException e) {
		ErrorLogger.getInstance().getLogger().fine(e.getMessage());
		return;
	}
	}
	
	private static void changeKeyToString(JSONObject current, String keyToChange)
	{
		current.put(keyToChange, current.get(keyToChange).toString());
	}
	
	
	private static void normalizeJsonArray(String arrKey, String keyToChange, JSONObject report, String... path)
	{
		try {
			JSONObject temp = report;
			
			for (String key : path) {
				temp = temp.getJSONObject(key);
				if (temp == null) {
					return;
				}
					
			}
			
			JSONArray arr = temp.optJSONArray(arrKey);
			
			if (arr == null) {
				// sometimes it's just an object
				JSONObject obj = temp.optJSONObject(arrKey);
				if (obj == null) return;
				
				if (obj.optJSONObject(keyToChange) == null) {
					addDummyValue(obj, keyToChange);
				}
				return;
			}
			
			for (int i = 0; i < arr.length(); i++) {
				JSONObject current = arr.getJSONObject(i);
				if (current.optJSONObject(keyToChange) == null) {
					addDummyValue(current, keyToChange);
				}
			}

		} catch (JSONException e) {
			ErrorLogger.getInstance().getLogger().fine(e.getMessage());
			return;
		}
	}
	
	private static void addDummyValue(JSONObject current, String keyToChange)
	{
		current.putOpt(keyToChange, dummy);
	}
}
