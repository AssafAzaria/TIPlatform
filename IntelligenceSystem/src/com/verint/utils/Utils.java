package com.verint.utils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.DatatypeConverter;

import org.apache.tika.Tika;
import org.apache.tika.mime.MediaType;

/**
 * Some util functions
 * @author Assaf Azaria
 *
 */
public class Utils {
	private static Logger logger = ErrorLogger.getInstance().getLogger();
	public static final MediaType PCAP_MIME = MediaType.parse("application/vnd.tcpdump.pcap");
	
	/** Instance of Tika facade class with default configuration. */
	private static final Tika defaultTika = new Tika();
	
	/**
	 * Identify file type of file with provided path
	 * Tika's default configuration.
	 *
	 * @param file Path of file for which file type is desired.
	 * @return Type of file for which file name was provided.
	 */
	public static String identifyFileType(final Path file) throws IOException
	{
		return defaultTika.detect(file);
	}
	
	/**
	 * Identify file type of file with provided name. Note - this uses only the file
	 * name, which is much simpler, but less accurate
	 *
	 * @param fileName Name of file for which file type is desired.
	 * @return Type of file for which file name was provided.
	 */
	public static String identifyFileTypeUsingNameOnly(final String fileName)
	{
		return defaultTika.detect(fileName);
	}

	// Generate a SHA256 hash of the given file
	public static String generateSHA256Hash(Path file) throws IOException
	{
		try{
			return generateFileHash(file, "SHA-256");
		}
		catch (NoSuchAlgorithmException e){
			// this 'cannot' happen.
			logger.log(Level.WARNING, "problem with hash generation", e); 
			return "";
		}
	}
	
	// Generate a MD5 hash of the given file
	public static String generateMD5Hash(Path file) throws IOException
	{
		try{
			return generateFileHash(file, "MD5");
		}
		catch (NoSuchAlgorithmException e){
			// this 'cannot' happen.
			logger.log(Level.WARNING, "problem with hash generation", e); 
			return "";
		}
	}
	
	// Generate a hash of the given file
	private static String generateFileHash(Path file, String algorithm) throws IOException, NoSuchAlgorithmException
	{
		MessageDigest md = MessageDigest.getInstance(algorithm);
		md.update(Files.readAllBytes(file));
		byte[] digest = md.digest();
		
		return DatatypeConverter.printHexBinary(digest).toUpperCase();
	}
	
	// just to avoid the exception
	public static void sleep(int seconds)
	{
		try {
			TimeUnit.SECONDS.sleep(seconds);
		} catch (InterruptedException e) {
			logger.finer("interrupted on sleep, fine");
		}
	}
	
	// Set logger levels 
	public static void setLoggerLevels(boolean verbose)
	{
		// default mode shows only info and above (warning, severe)
		Arrays.stream(logger.getHandlers()).filter(h -> h instanceof ConsoleHandler)
				.forEach(h -> h.setLevel(verbose ? Level.ALL : Level.INFO));
		logger.setLevel(Level.ALL);
	}
	
	public static void shutdownExecutor(ExecutorService service, int delaySecs)
	{
		service.shutdown(); // Disable new tasks from being submitted
        try {
            // Wait a while for existing tasks to terminate
            if (!service.awaitTermination(delaySecs, TimeUnit.SECONDS)) {
            	service.shutdownNow(); // Cancel currently executing tasks
                logger.info("forcing shut down");
            	
                // Wait a while for tasks to respond to being cancelled
                if (!service.awaitTermination(15, TimeUnit.SECONDS))
                	logger.warning("Pool did not terminate");
            }
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
        	service.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
	}
	
	public static void makeSureExecutorGetsShutdownOnExit(ExecutorService service, int delaySecs)
	{
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
	    	shutdownExecutor(service, delaySecs);
		}));
	}
	
	// return a Path to the given uri. Create it if needed
	public static Path getPath(String uri) {
		// create the dir if it is not there
		Path path = Paths.get(uri);

		if (Files.notExists(path)) {
			try {
				Files.createDirectories(path);
			} catch (IOException e) {
				logger.info("error creating " + uri + " directory " + e.getMessage());
			}
		}

		return path;
	}
}
