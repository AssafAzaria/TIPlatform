package com.verint.tests;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;

import com.verint.utils.Config;
import com.verint.utils.Utils;

public class TestDate {

	public static void main(String[] args) {
//		EnumSet<CheckType> status = EnumSet.noneOf(CheckType.class);
//		System.out.println(status);
//		
//		status.add(CheckType.EDR);
//		
//		System.out.println(status);
//		
//		status.add(CheckType.CNC);
//		
//		System.out.println(status);
		
		Path reportsDir = Utils.getPath(Config.getDailyReportPath());
		String today = LocalDate.now().toString();
		Path reportsPath = reportsDir.resolve("report" + today + ".txt");

		System.out.println(reportsPath);
		
	}

	// Builds the index name used by the EDR server.
	// [hash]-events-[yyMMdd]
	@SuppressWarnings("unused")
	private String getEsIndexName(String hash) {
		String now = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
		return new StringJoiner("-")
				.add(hash)
				.add("events")
				.add(now).toString();
		
	}

}
