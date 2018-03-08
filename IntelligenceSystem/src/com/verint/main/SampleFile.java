package com.verint.main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.logging.Logger;

import org.apache.tika.mime.MediaType;

import com.verint.main.FileDetails.Maliciousness;
import com.verint.utils.ErrorLogger;
import com.verint.utils.Utils;

/**
 * Represents a file submitted on our system. Includes additional file data  
 * 
 * @author Assaf Azaria
 */
public class SampleFile {
	private static final String PARAMS_PATH = "file_params";
	private static final String PARAMS_SUFFIX = ".txt";
	
	private Logger logger = ErrorLogger.getInstance().getLogger();
	
	// The file path
	private Path path;

	// The running context for the file.
	private final Context context;

	// The sha256 hash
	private final String sha256Hash;

	// The md5 hash
	private final String md5Hash;

	// The elastic search id we use for this file
	private final String esId;

	// Externally supplied parameters
	private FileDetails fileDetails;
	
	// The expected tests this sample should follow. 
	private EnumSet<CheckType> expectedTests = EnumSet.allOf(CheckType.class); 
	
	// The list of tests performed on this sample
	private EnumMap<CheckType, PerformedTest> performedTests = new EnumMap<>(CheckType.class);
	
	// The file mime type
	private MediaType mimeType; 
	
	// TODO:?
	private int attempts = 0;
	
	public SampleFile(Path path)
	{
		this(path, new FileDetails());
	}
	
	public SampleFile(Path path, FileDetails details) {
		this (path, MediaType.EMPTY, details);
	}
	
	public SampleFile(Path path, MediaType type, FileDetails details) {
		// default context
		this(path, type, new Context(), details);
	}
	
	public SampleFile(Path path, MediaType type, Context ctx, FileDetails details) 
	{
		this.path = path;

		// default settings
		this.context = ctx;

		this.sha256Hash = createSha256Hash().toLowerCase(); // ES likes lower case
		
		this.md5Hash = createMd5Hash().toLowerCase(); // ES likes lower case
		
		this.esId = createEsId();
		
		this.mimeType = type;
		if (mimeType == MediaType.EMPTY) {
			mimeType = calcMimeType();
		}
		
		this.fileDetails = details;
		
		// Check if there are more specific params for this file
		loadSpecificDetails();
	}
	
	
	public void setExpectedTests(EnumSet<CheckType> expectedTests)
	{
		this.expectedTests = expectedTests;
	}

	
	private final String createSha256Hash() {
		try {
			return Utils.generateSHA256Hash(path);
		} catch (IOException e) {
			ErrorLogger.getInstance().getLogger().severe("hash problem");
			return "";
		}
	}
	
	private final String createMd5Hash() {
		try {
			return Utils.generateMD5Hash(path);
		} catch (IOException e) {
			ErrorLogger.getInstance().getLogger().severe("md5 hash problem");
			return "";
		}
	}

	private final String createEsId() {
		return sha256Hash + Instant.now();
	}
	
	public Path getPath() {
		return path;
	}

	public String getSha256Hash()
	{
		return sha256Hash;
	}
	
	public String getMd5Hash()
	{
		return md5Hash;
	}

	public Context getContext() {
		return context;
	}

	public boolean isPcap()
	{
		return mimeType.equals(Utils.PCAP_MIME);
	}
	
	public FileDetails getFileDetails()
	{
		return (fileDetails != null) ? fileDetails : new FileDetails();
	}
	
	public String getSource() {
		return (fileDetails  != null) ? fileDetails .getFileSource() : "";
	}
	
	public Maliciousness getMaliciousness()
	{
		return (fileDetails != null) ? fileDetails.getMaliciousness() : Maliciousness.UNKNOWN;
	}
	
	public String getSearchString()
	{
		return (fileDetails != null) ? fileDetails.getSearchString() : "";
	}
	
	public String getEsId() {
		return esId;
	}
	
	public MediaType getMimeType()
	{
		return mimeType;
	}
	
	public void addPerformedTest(PerformedTest test)
	{
		performedTests.put(test.getCheckType(), test);
	}
	
	public PerformedTest getPerformedTest(CheckType type)
	{
		return performedTests.get(type);
	}
	
	public boolean isProcessingFinished()
	{
		// Check if all expected tests were performed
		boolean result = performedTests.keySet().containsAll(expectedTests);
		
		logger.info("-----------------------------------------");
		logger.info("Main: checking if finished with file " + path);
		logger.fine("Main: STATUS: " + performedTests + " Expected: " + expectedTests);
		logger.fine("Returning: " + result);
		
		return result;
	}
	
	private final MediaType calcMimeType()
	{
		try {
			String type = Utils.identifyFileType(path);
			return MediaType.parse(type);
		
		} catch (IOException e) {
			logger.fine("SampleFile: cannot detect mime type");
			return MediaType.EMPTY;
		}
	}
	
	public int incAndGetAttempts()
	{
		return ++attempts;
	}
	
	public void resetAttempts()
	{
		attempts = 0;
	}
	
	// 
	// File details can be specified specifically for a file, using 'file_name.txt' on
	// 'file_params' dir. Or they can be for an entire dir 'params.txt'
	public final void loadSpecificDetails()
	{
		// Check if there are parameters associated specifically with this file.
		String pathWithSuffix = path.getFileName() + PARAMS_SUFFIX;
		Path paramsPath = Utils.getPath(PARAMS_PATH).resolve(pathWithSuffix);
		
		// load file params
		if (Files.exists(paramsPath)) {
			fileDetails = new FileDetails();
			fileDetails.loadFromFile(paramsPath);
		}					
			
	}
		
	@Override
	public String toString() {
		return String.format("SampleFile [path=%s, context=%s, hash=%s, mime=%s]", 
				path, context, sha256Hash, mimeType);
	}
	
	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((context == null) ? 0 : context.hashCode());
		result = prime * result + ((esId == null) ? 0 : esId.hashCode());
		result = prime * result + ((sha256Hash == null) ? 0 : sha256Hash.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof SampleFile))
			return false;
		SampleFile other = (SampleFile) obj;
		if (context == null) {
			if (other.context != null)
				return false;
		} else if (!context.equals(other.context))
			return false;
		if (esId == null) {
			if (other.esId != null)
				return false;
		} else if (!esId.equals(other.esId))
			return false;
		if (sha256Hash == null) {
			if (other.sha256Hash != null)
				return false;
		} else if (!sha256Hash.equals(other.sha256Hash))
			return false;
		return true;
	}

	
}
