package com.verint.main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.verint.utils.ErrorLogger;

// Additional params for a sample file (or bunch of files)
public class FileDetails
{
	public enum Maliciousness {MALICIOUS, BENIGN, UNKNOWN};
	
	private Logger logger = ErrorLogger.getInstance().getLogger();
	
	// VT search string (if exists)
	private String searchString = "";
		
	// running parameters
	private List<String> params = new ArrayList<>();
	
	// the source of the file (VirusTotal, System32, etc)
	private String fileSource = "file";
	
	// Maliciousness of the file
	private Maliciousness maliciousness = Maliciousness.UNKNOWN;
	
	public FileDetails(){
	}
	
	public Maliciousness getMaliciousness()
	{
		return maliciousness;
	}
	
	public String getSearchString()
	{
		return searchString;
	}
	
	public String getFileSource()
	{
		return fileSource;
	}
	
	public void setFileSource(String fileSource)
	{
		this.fileSource = fileSource;
	}
	
	public List<String> getParams()
	{
		return params;
	}
	
	public void loadFromFile(Path paramsFile)
	{
		// load file params
		try {
			logger.fine("loading params from file: " + paramsFile.getFileName());
						
			// read the file
			String content = new String(Files.readAllBytes(paramsFile));
			JSONObject json = new JSONObject(content);
			
			// source
			this.fileSource = json.optString("source");
			
			// maliciousness
			String malStr = json.optString("maliciousness");
			
			try{
				this.maliciousness = Maliciousness.valueOf(malStr.toUpperCase());
			}catch(IllegalArgumentException e){
			}
			
			// params
			JSONArray arr = json.getJSONArray("params");
			for (int i = 0; i < arr.length(); i++){
				this.params.add(arr.get(i).toString());
			}
			
			// search string
			this.searchString = json.optString("search");
		} catch (IOException | JSONException e) {
			logger.finer("problem reading file params " + e.getMessage());
		}
	}

	@Override
	public String toString()
	{
		return String.format("FileParams [searchString=%s, params=%s, fileSource=%s, maliciousness=%s]", searchString,
				params, fileSource, maliciousness);
	}
	
	

}
