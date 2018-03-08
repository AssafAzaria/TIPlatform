package com.verint.utils;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.logging.Logger;

import com.verint.main.CheckType;
import com.verint.main.PerformedTest;
import com.verint.main.SampleFile;

public class DailyReportGenerator
{
	private Path reportsDir, reportsFile; 
	private Logger logger = ErrorLogger.getInstance().getLogger();
	
	public DailyReportGenerator()
	{
		reportsDir = Utils.getPath(Config.getDailyReportPath());
	}
	
	public void addLineToReport(SampleFile file){
		reportsFile = getDailyReportFile();
		
		// TODO: maybe minimze number of writer object
		try(PrintWriter out = new PrintWriter(new FileWriter(reportsFile.toFile(), true))){
			printRow(out, file.getPath().getFileName().toString()
					, file.getSha256Hash()
					, getCheckResult(file, CheckType.EDR)
					, getCheckResult(file, CheckType.PAYLOAD)
					, getCheckResult(file, CheckType.CNC));
					
		}
		catch(IOException e){
			logger.warning("Problem writing to report file: " + e.getMessage());
		}
	}
	
	private String getCheckResult(SampleFile file, CheckType type)
	{
		PerformedTest test = file.getPerformedTest(type);
		if (test == null) return "not supported";
		
		if (!test.isSupported()) return "not supported";
		
		return test.isSuccess() ? "passed" : "failed";
		
	}
	
	
	private Path getDailyReportFile()
	{
		String today = LocalDate.now().toString();
		if (reportsFile != null && reportsFile.toString().contains(today)) {
			System.out.println("existing file");
			
			return reportsFile;
		}
		
		// create a new one
		reportsFile = reportsDir.resolve("report" + today + ".txt");
		if (Files.notExists(reportsFile)) {
			System.out.println("creating new file");
			createReportFile(reportsFile);
		}
		
		return reportsFile;
	}
	
	private void createReportFile(Path reportFile)
	{
		try {
			Files.createFile(reportFile);
			PrintWriter out = new PrintWriter(reportFile.toFile());
			printRow(out, "File_Name", "Sha-256_Hash", "EDR", "Sandbox", "CNC");
			printRow(out, "---------------", "---------------", "-------------", "-------------", "-------------");
			out.close();
		 } catch (IOException e) {
			logger.warning("Problem creating reports file: " + e.getMessage());
		}
	}

	private static void printRow(PrintWriter writer, String name, String hash, String edr, 
			String sandbox, String cnc) {
	    writer.printf("%-50s %-70s %-15s %-15s %-15s %n", name, hash, edr, sandbox, cnc);
	}
	
	
}
