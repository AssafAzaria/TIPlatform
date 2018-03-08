package com.verint.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class TestParams
{

	private static final String PARAMS_PATH = "file_params";
	private static final String PARAMS_SUFFIX = ".params";
	
	
	// Check if there are parameters associated with this exe. 
	public static List<String> getExeParams(Path exe)
	{
		List<String> params  = new ArrayList<>();
		
		String pathWithSuffix = exe.getFileName() + PARAMS_SUFFIX;
		Path paramsPath = Paths.get(PARAMS_PATH).resolve(pathWithSuffix);
		
		try {
			if (Files.exists(paramsPath))
			{
				// read the file
				String content = new String(Files.readAllBytes(paramsPath));
			
				JSONObject json = new JSONObject(content);
				JSONArray arr = json.getJSONArray("params");
				System.out.println(arr);
				for (int i = 0; i < arr.length(); i++)
				{
					params.add(arr.get(i).toString());
				}
			}
		} catch (IOException e) {
			
		}
		
		return params;
	}
	
	public static void main(String[] args)
	{
		Path file = Paths.get("files_to_test\\getright_setup (3).exe");
		// System.out.println(getExeParams(file));
		//SampleFile fi = new SampleFile(Paths.get("files_to_test\\getright_setup (3).exe"));
	}

}
