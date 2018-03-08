package com.verint.virustotal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.client.utils.URIBuilder;
import org.json.JSONArray;
import org.json.JSONObject;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;
import com.verint.utils.Utils;

/**
 * Download the top-n results of a given Intelligence search.
 * 
 * Code is based on existing phyton script vt_intelligence_downloader
 * 
 * @author Assaf Azaria
 */
public class VirusTotalDownloader
{
	private static final String API_KEY = "d8fef41c11123ced2689891b75d6092d515ee89c1de21519e16d464144a4a267";
	private static final String VT_SEARCH_URL = "https://www.virustotal.com/intelligence/" + "search/programmatic/";
	private static final String VT_DOWNLOAD_URL = "https://www.virustotal.com/intelligence/" + "download/";
	private static final int CONCUR_DOWNLOADS = 5;
	
	// The download folder. Note - do not use the same downloader object for 2 
	// different concurrent requests, it may cause dir problems. see (main)
	private Path downloadFolder;
	private Logger logger = ErrorLogger.getInstance().getLogger();
	private BlockingQueue<String> downloadQueue = new LinkedBlockingQueue<>();
	private ExecutorService executor; 
	private volatile boolean stopAllThreads = false;
	
	public VirusTotalDownloader()
	{
		// Sets http request default header
		Unirest.setDefaultHeader("Accept", "application/json");
		//Utils.makeSureExecutorGetsShutdownOnExit(executor);
	}

	/**
	 * Download the top-n results of a given Virus Total search
	 * 
	 * @param search
	 *            the search string
	 * @param numFiles
	 *            how many files to download
	 */
	public void getFilesFromVT(String search, int numFiles)
	{
		getFilesFromVT(search, numFiles, true);
	}
	
	/**
	 * Download the top-n results of a given Virus Total search
	 * 
	 * @param search
	 *            the search string
	 * @param numFiles
	 *            how many files to download
	 * @param writeQuery whether to write a file with the search string in the downloads dir
	 */
	public void getFilesFromVT(String search, int numFiles, boolean writeQuery)
	{
		executor = createDaemonThreadPool(CONCUR_DOWNLOADS);
		//this.searchString = search;
		
		logger.info("----- Starting VirusTotal downloader");
		logger.info("----- VirusTotal search: " + search);
		logger.info("----- Files to download: " + numFiles);

		logger.info("----- Creating folder to store the requested files");
		
		// As long as each run of this function is from its own object (See main)
		// there will be no collision. (We can use ThreadLocal if needed).
		downloadFolder = createDownloadFolder(search, writeQuery);
		
		// Launch threads on queue to download.
		executor.submit(() -> pollQueueAndDownload());

		logger.info("----- Getting hashes from VT");

		int queuedFiles = 0;
		boolean finished = false;
		String nextPage = "undefined";
		while (!finished) {
			try {
				VTResponse res = getMatchingFiles(search, nextPage);
				logger.info("----- Retrieved " + res.getHashes().size()
						+ " matching files in current page, queueing them for download");

				int leftToQueue = numFiles - queuedFiles;
				queuedFiles += queueVTResponse(res, leftToQueue);
				logger.info("--- queued " + queuedFiles + " hashes");

				if (!res.hasNextPage() || queuedFiles >= numFiles) {
					logger.info("---- no more matching files");
					finished = true;
				}

				nextPage = res.getNextPage();
			} catch (InvalidQueryException e) {
				logger.severe("Invalid search " + e.getMessage());
				return;
			}
		}

		waitForDownloadsAndShutdown();
		logger.info("----- Downloaded files have been saved in " + downloadFolder);
		
		
		//shutdownThreads();
	}

	// Queues 'num files' from the given response, returns the number of queue
	// files
	private int queueVTResponse(VTResponse res, int numFiles)
	{
		int queued = 0;
		for (String hash : res.getHashes()) {
			try {
				downloadQueue.put(hash);
				queued++;
			} catch (InterruptedException e) {
				// shouldn't happen
				logger.fine("--- queue operation interrupted " + e.getMessage());
			}

			if (queued >= numFiles) {
				logger.info("Queued requested number of files");
				break;
			}
		}

		return queued;
	}

	/**
	 * Downloads the file with the given hash from VT.
	 * 
	 * @param file_hash:
	 *            either the md5, sha1 or sha256 hash of a file in VirusTotal.
	 * @param destination_file:
	 *            full path where the given file should be stored.
	 * 
	 * @return True if the download was successful, False if not.
	 */
	private boolean downloadFile(String fileHash, Path destFile)
	{
		URL url = null;
		try {
			url = new URIBuilder(VT_DOWNLOAD_URL)
					.addParameter("hash", fileHash)
					.addParameter("apikey", API_KEY)
					.build().toURL();
		} catch (Exception e) {
			logger.info("Url syntax exception: " + e.getMessage());
		}

		// try 3 times
		for (int attempts = 0; attempts < 3; attempts++) {
			try (InputStream in = url.openStream()) {
				Files.copy(in, destFile, StandardCopyOption.REPLACE_EXISTING);
				return true;
			} catch (IOException e) {
				logger.info("Attempt " + attempts + " problem downloading file: " +
						fileHash + " : " + e.getMessage());
			}
		}
		return false;
	}
	
	// Poll the queue for hashes, and download the file.
	private void pollQueueAndDownload()
	{
		while (! (downloadQueue.isEmpty() && stopAllThreads)) {
			try {
				String hash = downloadQueue.take();
				Path saveFile = downloadFolder.resolve(hash);

				logger.info("Downloading file " + hash);
				boolean success = downloadFile(hash, saveFile);

				logger.info(hash + " download " + (success ? "was successfull" : "failed"));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Gets a page of files matching a given search.
	 * 
	 * @param search
	 *            a VirusTotal Intelligence search phrase. More about
	 *            Intelligence searches at:
	 *            https://www.virustotal.com/intelligence/help/
	 * @param page
	 *            a token indicating the page of file results that should be
	 *            retrieved.
	 * 
	 * @return Tuple with a token to retrieve the next page of results and a
	 *         list of sha256 hashes of files matching the given search
	 *         conditions.
	 * 
	 * @exception: InvalidQueryException:
	 *                 if the Intelligence query performed was not valid.
	 */
	private VTResponse getMatchingFiles(String search, String page) throws InvalidQueryException
	{
		int attempts = 0;

		JSONObject jsonData = null;
		while (attempts < 10) {
			try {
				HttpRequest req = Unirest.get(VT_SEARCH_URL)
						.queryString("query", search)
						.queryString("apikey", API_KEY)
						.queryString("page", page);

				HttpResponse<JsonNode> res = req.asJson();
				jsonData = res.getBody().getObject();
				break; 
			} catch (UnirestException e) {
				logger.log(Level.INFO, "Connection problem. Attempt: " + attempts, e);
				attempts++;
				Utils.sleep(1);
			}
		}

		if (jsonData == null || !jsonData.has("result")) {
			throw new InvalidQueryException(jsonData.optString("error", "no result"));
		}

		String nextPage = jsonData.getString("next_page");
		List<String> hashes = getHashes(jsonData);

		return new VTResponse(hashes, nextPage);
	}

	private List<String> getHashes(JSONObject json)
	{
		List<String> hashes = new ArrayList<>();
		JSONArray arr = json.getJSONArray("hashes");
		for (int i = 0; i < arr.length(); i++)
			hashes.add(arr.getString(i));

		return hashes;
	}

	/**
	 * Creates a folder to store the downloaded files. The query itself can be
	 * stored in a separate txt file inside the directory created
	 * 
	 * @param query
	 *            - the search query, as a string, that is issued in order to
	 *            save the corresponding files to the directory being created.
	 * @param writeQuery whether to write the query in a separate file
	 * @return the path object to the created file.
	 */
	private Path createDownloadFolder(String query, boolean writeQuery)
	{
		DateTimeFormatter tSPattern = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
		Path localDir = Paths.get(Config.getVtDownloadPath());
		Path subFolder = localDir.resolve(LocalDateTime.now().format(tSPattern));

		try {
			if (Files.notExists(localDir)) {
				Files.createDirectories(localDir);
			}

			if (Files.notExists(subFolder)) {
				Files.createDirectories(subFolder);
			}

			if (writeQuery && query != null) {
				Path queryPath = subFolder.resolve(Config.getParamsPath());
				Files.write(queryPath, createJsonParamsFile(query).getBytes());
			}
		} catch (IOException e) {
			logger.finer("problem creating dirs, or params file" + e.getMessage());
		}
		return subFolder;
	}

	private String createJsonParamsFile(String search)
	{
		JSONObject obj = new JSONObject();
		obj.put("source", "VirusTotal");
		obj.put("params", "[]");
		obj.put("maliciousness", "unknown");
		obj.put("search", search); 
		
		return obj.toString(2);
	}

	private void waitForDownloadsAndShutdown()
	{
		stopAllThreads = true;
		executor.shutdown();
		try {
			logger.info("---- waiting for downloads to finish");
			executor.awaitTermination(1, TimeUnit.HOURS);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		executor.shutdownNow();
	}
	
	/**
	 * Returns a fixed executor service, with daemon threads
	 * 
	 * @return
	 */
	private ExecutorService createDaemonThreadPool(int threadNum)
	{
		return Executors.newFixedThreadPool(threadNum, (r) -> {
			Thread t = Executors.defaultThreadFactory().newThread(r);
			//t.setDaemon(true);
			return t;
		});
	}
	
	// test
	public static void main(String[] args)
	{
		String search = "tag:peexe NOT tag:corrupt and positives:5+";
		int numFiles = 5;
		
		if (args != null && args.length > 0){
			search = args[0];
			
			if (args.length > 1) {
				try{
					numFiles = Integer.parseInt(args[1]);
				}catch(NumberFormatException e){}
			}
		}
		
		VirusTotalDownloader vt = new VirusTotalDownloader();
		vt.getFilesFromVT(search, numFiles);
	}

}
