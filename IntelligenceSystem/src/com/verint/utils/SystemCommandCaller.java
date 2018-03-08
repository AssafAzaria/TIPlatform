package com.verint.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A utility class for invoking a native system call (on ubuntu)
 * Note: on ubuntu this must be run with root priviliges!
 * 
 * @author Assaf Azaria
 */
public class SystemCommandCaller {
	private static Logger logger = ErrorLogger.getInstance().getLogger();
	
	
	/**
	 * Run a system call. 
	 * @param command the process to run (e.g. bash)
	 * @param cmdArgs arguments (e.g. -c etc.)
	 * @return the process output
	 */
	public static Optional<List<String>> invoke(String command, List<String> cmdArgs)
	{
		return invoke(null, command, cmdArgs);
	}
	
	/**
	 * Run a system call.
	 * 
	 * @param dir the directory in which to run the command 
	 * @param command the process to run (e.g. bash)
	 * @param cmdArgs arguments (e.g. -c etc.)
	 * @return the process output
	 */
	public static Optional<List<String>> invoke(String dir, String command, List<String> cmdArgs)
	{
		Future<List<String>> result = null;
		
		// Check os
		boolean isWindows = System.getProperty("os.name").
				toLowerCase().startsWith("windows");

		// We don't support windows (we can add it later if needed).
		if (isWindows)
		{
			logger.info("SystemCommandCaller: windows os not supported");
			return Optional.empty();
		}
		
		List<String> fullCommand = getFullCommand(command, cmdArgs);
		logger.info("Issuing command: " + fullCommand);
		Process process;
		try {
			ProcessBuilder builder = new ProcessBuilder();
			builder.command(fullCommand);
			if (dir != null) builder.directory(new File(dir));
		
			logger.finer("Launching process: " + builder.command() + 
						 " on dir: " + builder.directory());
			process = builder.start();
			
			// Read the output and return it
			ExecutorService s = Executors.newSingleThreadExecutor();
			result = s.submit(() -> {
				return new BufferedReader(
							new InputStreamReader(
								process.getInputStream()))
									.lines().collect(Collectors.toList());
			});
			int exitCode = process.waitFor();
			assert exitCode == 0; 
			s.shutdown(); 
		} catch (IOException | InterruptedException e) {
			logger.finer("problem " + e);
			return Optional.empty();
		}
		
		try {
			return Optional.ofNullable(result.get(2, TimeUnit.SECONDS));
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			// No need to pass the exception, because we do not really care 
			// about the results
			return Optional.empty();
		}

	}

	private static List<String> getFullCommand(String cmd, List<String> cmdArgs)
	{
		List<String> full = new ArrayList<>();
		full.add(cmd);
		full.addAll(cmdArgs);
		return full;
	}
}
