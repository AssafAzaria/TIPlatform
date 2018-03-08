package com.verint.cnc.main;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListener;
import org.apache.commons.io.input.TailerListenerAdapter;

import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;

/**
 * Tail a log file, wait a pattern to show up.
 * 
 * @author Assaf Azaria
 */
public class LogFileTailer implements Runnable{
	private static final String DEFAULT_LOG_FILE = Config.getDDPLogFile();
	
	private Logger logger = ErrorLogger.getInstance().getLogger();
	
	// The pattern we are looking for 
	private Pattern pattern;
	private Path logFile;
	private int delayMillis;
	private Tailer tailer; // apache tailer
	private TailerListener outerListener;
	
	// We catch notifications inside, passing only those who match the pattern
	private TailerListener innerListener = new TailerListenerAdapter(){
		@Override
		public void handle(String line) {
			if (pattern.matcher(line).find()){
				outerListener.handle(line);
			}
		}
		
		@Override
		public void handle(final Exception ex) {
			// this is called by executor to shut the tailer down
			if (ex instanceof InterruptedException)
			{
				stopTailing();
				return;
			}
			// just log it for now
			logger.info("CNC: Tailer exception, see log. msg: " + ex.getMessage());
			logger.log(Level.SEVERE, "", ex);
		}
	};
	
	public LogFileTailer(TailerListener listener)
	{
		this(Paths.get(DEFAULT_LOG_FILE), listener);
	}
	
	public LogFileTailer(Path logFile, int delayMillis, TailerListener listener)
	{
		this(logFile, 
			 "transfers: 0", //default pattern 
			 delayMillis, // default delay between checks
			 listener);
	}
	public LogFileTailer(Path logFile, TailerListener listener)
	{
		this(logFile, 
			 "transfers: 0", //default pattern 
			 300, // default delay between checks
			 listener);
	}
	
	public LogFileTailer(Path logFile, String pattern, int delayMillis, TailerListener listener)
	{
		this.logFile = logFile;
		this.pattern = Pattern.compile(pattern);
		this.delayMillis = delayMillis;
		this.outerListener = listener;
	}
	
	// Tail the log file until the pattern is found. 
	@Override
	public void run()
	{
		tailer = new Tailer(logFile.toFile(), innerListener, delayMillis, 
				true, // Read from end of file
				true);// Close and reopen file between chunks 
	    
		// This method blocks. 
		tailer.run(); 
		logger.info("Tailer exiting");
	}
	
	public void stopTailing()
	{
		if (tailer != null)
			tailer.stop();
	}
	
	
}