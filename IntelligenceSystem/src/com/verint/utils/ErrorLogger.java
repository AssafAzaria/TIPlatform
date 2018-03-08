package com.verint.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

// Use 2 handlers. One for log files with level.all, and one for console output 
// Levels: severe - for serious errors, info - for info messages, 
// fine - only in verbose mode
public class ErrorLogger {
	private static ErrorLogger instance = new ErrorLogger();
	private static final String LOGS_FOLDER = "logs";
	private Logger logger;
	public static final int FILE_SIZE = 1024;
	
	private ErrorLogger() {
		// set logger format
		System.setProperty("java.util.logging.SimpleFormatter.format", 
				"%4$s: %5$s [%1$tb %1$td %1$tT] %6$s%n");	
		logger = Logger.getAnonymousLogger();
		configure();
	}

	public static ErrorLogger getInstance() {
		return instance;
	}

	private void configure() {
		try {
			// File handler
			Files.createDirectories(Paths.get(LOGS_FOLDER));
			String fileName = LOGS_FOLDER + File.separator + getCurrentTimeString() + ".log";
			FileHandler fileHandler = new FileHandler(fileName, FILE_SIZE, 10); // 10 rolling files
			logger.addHandler(fileHandler);
			fileHandler.setLevel(Level.ALL);
			fileHandler.setFormatter(new SimpleFormatter());

			// Console handler
			Handler console = new ConsoleHandler();
			console.setFormatter(new SimpleFormatter());
			console.setLevel(Level.INFO);
			logger.addHandler(console);
			
			// Prevent logs from being processed by default Console handler.
			logger.setUseParentHandlers(false); 
			logger.setLevel(Level.ALL);
		} catch (IOException e) {
			e.printStackTrace();
		}

		addCloseHandlersShutdownHook();
	}

	private void addCloseHandlersShutdownHook() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			// Close all handlers to get rid of empty .LCK files
			for (Handler handler : logger.getHandlers()) {
				handler.close();
			}
		}));
	}

	private String getCurrentTimeString() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss"));
	}

	public Logger getLogger() {
		return logger;
	}

	public void log(Exception e) {
		logger.log(Level.SEVERE, "", e);
	}
}