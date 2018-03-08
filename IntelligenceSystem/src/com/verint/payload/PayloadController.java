package com.verint.payload;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.apache.tika.mime.MediaType;
import org.json.JSONObject;

import com.mashape.unirest.http.JsonNode;
import com.verint.es.ESHandler;
import com.verint.exceptions.FailReason;
import com.verint.exceptions.FileSubmittionFailedException;
import com.verint.main.AsyncEngineController;
import com.verint.main.CheckType;
import com.verint.main.ControllerData;
import com.verint.main.EngineListener;
import com.verint.main.Response;
import com.verint.main.SampleFile;
import com.verint.payload.data.PayloadPcapResponse;
import com.verint.payload.data.PayloadResponse;
import com.verint.utils.ErrorLogger;
import com.verint.utils.PayloadJsonNormalizer;
import com.verint.utils.Utils;

/**
 * A controller for payload data. Works asynchronously because of 
 * the gap between scanning a file and getting it's data.
 * 
 * @author Assaf Azaria
 */
public class PayloadController implements AsyncEngineController
{
	private PayloadApi api = new PayloadApi();
	private Logger logger = ErrorLogger.getInstance().getLogger();
	private Path pcapFolder = Utils.getPath("pcaps");
	private ControllerData data = createControllerData();
	
	// A timer we use to wait for reports on scanned files
	// (core size 0 should have been better, but it hangs cpu on ubuntu. 
	// See https://bugs.openjdk.java.net/browse/JDK-8129861)
	private ScheduledExecutorService timer = Executors.newScheduledThreadPool(1); 
	
	// This data structure gives best performance for iteration - which is what we 
	// do here mostly. (note that size() is not constant time here, see docs)
	private Queue<SampleFile> waitingFilesQueue = new ConcurrentLinkedQueue<>();
	
	// CopyOnWrite gives best performance for listeners, see docs.
	private List<EngineListener> listeners = new CopyOnWriteArrayList<>();
	
	// The timer task, which is suspended and resumed when needed.
	private ScheduledFuture<?> task;
	
	//
	// Listeners registration
	//
	@Override
	public void addListener(EngineListener l)
	{
		listeners.add(l);
	}

	@Override
	public void removeListener(EngineListener l)
	{
		listeners.remove(l);
	}
	
	// Here we decide what mime-types are supported by this controller, among
	// other things
	private ControllerData createControllerData()
	{
		// Currently support all 'application' mime types except pcap.
		Predicate<MediaType> isSupported = 
				(type) -> (type.getType().equals("application") &&
						   !type.equals(Utils.PCAP_MIME));
		
		return new ControllerData(CheckType.PAYLOAD, isSupported);
	}
		
	/**
	 * Submit file to payload security. The immediate response is a json 
	 * containing the file's hash, or an error response code.
	 * 
	 * The actual response - json report and pcap file, will be dispatched 
	 * to listeners on a different thread
	 * @param file the file to submit
	 * @see addPayloadListener
	 */
	public PayloadResponse scanFile(SampleFile file)
	{
		logger.info("PAYLOAD: payload scanning file: " + file.getPath());
		
		// First, try to get the report, in case the file was allready scanned
		logger.fine("PAYLOAD: Trying to get report first");
		PayloadResponse report = getJsonReport(file);
		if (report != null && !report.isError()){
			handleReportResponse(report, file);
			return report;
		}
		
		// Send the file to scan
		PayloadResponse res = null;
		try{
			res = api.scanFile(file.getPath().toFile());
		}catch(PayloadException e){
			// http errors mostly
			throw new FileSubmittionFailedException(file.getPath(), FailReason.HTTP_ERROR, 
					e.getMessage(), e);
		}
		
		// logical error
		if (res.isError()){
			logger.log(Level.SEVERE, "PAYLOAD: Problem with scan: " + res.getError());
			throw new FileSubmittionFailedException(res.getError());
		}
	
		// Just a check (if they will sometime change the hash type). 
		if (!file.getSha256Hash().equalsIgnoreCase(res.getSha256()))
		{
			logger.log(Level.SEVERE, "PAYLOAD: Got a different hash form payload? " + res.getSha256());
			logger.log(Level.SEVERE, "Our hash: " + file.getSha256Hash());
		}
		
		// Start the polling timer if needed
		if(waitingFilesQueue.isEmpty()){
			resumeScheduler();
		}
		
		// Put the file on queue for reports polling
		waitingFilesQueue.add(file);
		logger.info("PAYLOAD: waiting for scan result: " + file.getPath());
		return res;
	}
	
	/**
	 * A way to get a json report synchronously
	 * @return an object containing the json report or an error response
	 */
	public PayloadResponse getJsonReport(SampleFile file)
	{
		PayloadResponse res = null;
		try{
			res =  api.getJsonReport(file.getSha256Hash());
		}catch(PayloadException e){
			// just log here:
			logger.log(Level.WARNING, e.getMessage(), e);
		}
		return res;
	}
	
	/**
	 * Get a pcap on a previously scanned file. You should call this function
	 * only after receiving a report, otherwise a pcap containing error response
	 * can be received.
	 */
	public PayloadPcapResponse getPcapOnScannedFile(SampleFile file)
	{
		String hash = file.getSha256Hash();
		logger.info("PAYLOAD: Asking for pcap on hash " + hash);
		
		PayloadPcapResponse pcapRes = null;
		try{
			pcapRes = api.getPcapReport(hash);
		}catch(PayloadException e){
			// just log here:
			logger.log(Level.WARNING, e.getMessage(), e);
		}
		// Save pcap to folder
		Path full = pcapFolder.resolve(hash + ".pcap");
		
		unzipAndWritePcap(full, pcapRes.getCompressedPcap());
		
		pcapRes.setPcapPath(full); 
		
		return pcapRes;
	}
	
	private void unzipAndWritePcap(Path path, byte[] compressedPcap) 
	{
		logger.fine("PAYLOAD: Unzipping pcap and saving to file: " + path);
		try (GZIPInputStream gzipIn = new GZIPInputStream(new ByteArrayInputStream(compressedPcap));
			OutputStream out = Files.newOutputStream(path))
		{	
			byte[] buff = new byte[512];
			int len = 0;

			while ((len = gzipIn.read(buff)) > 0) {
			    out.write(buff, 0, len);
			}
		 }catch(IOException e){
			pcapNotInGzipFormat(path, compressedPcap);
		 }
	}

	// This 'shouldn't' happen... 
	private void pcapNotInGzipFormat(Path full, byte[] pcap)
	{
		logger.warning("PAYLOAD: pcap not in gzip format");
		
		// save the file anyhow
		try {
			Files.write(full, pcap);
		} catch (IOException e) {
			logger.fine("error saving " + full);
		}
	}
	
	// Called by the timer/scheduler periodically until a report is ready,
	// then - notifies listeners.
	private void pollPayloadForReports()
	{
		logger.fine("PAYLOAD: Checking payload for reports. waiting files: " + 
				waitingFilesQueue.size());
		
		// go over all waiting samples, and try to get reports on them
		waitingFilesQueue.stream().forEach((sample) -> {
			PayloadResponse res = api.getJsonReport(sample.getSha256Hash());
			if (res != null && !res.isError()){
				sample.resetAttempts();
				handleReportResponse(res, sample);
			}
			
			// After 5 attempts, we cancel and send an error
			else if (sample.incAndGetAttempts() == 5){
				sample.resetAttempts();
				handleErrorResponse(res, sample);
			}
			
			if (waitingFilesQueue.isEmpty()) suspendScheduler();
		});
	}

	// Handles incoming report responses. Gets the pcap, and notify listeners
	private void handleReportResponse(PayloadResponse res, SampleFile sample)
	{
		logger.info("PAYLOAD: Got report from Payload Security on " + sample.getSha256Hash());
		
		// remove from queue
		waitingFilesQueue.remove(sample);
		
		res.getReport().ifPresent(report -> {
			// get the pcap also
			PayloadPcapResponse pcapRes = getPcapOnScannedFile(sample);
			
			// Reduce the report to fit to our needs
			PayloadJsonNormalizer.reduceReportToOurNeeds(report);
			
			// Payload report does not keep type structure that elastic needs. 
			PayloadJsonNormalizer.normalize(report);
					
			// index data on elastic
			ESHandler.getInstance().indexPayloadReport(sample, report);
			
			Response response = new Response(sample, CheckType.PAYLOAD, report, 
					pcapRes.getPcapPath());
			
			// notify listeners 
			listeners.stream().forEach(l -> l.onResponse(response));
		});
	}
	
	// Handles incoming report responses. Gets the pcap, and notify listeners
	private void handleErrorResponse(PayloadResponse res, SampleFile sample){
		logger.info("PAYLOAD: Cannot get report from Payload Security on " + 
				sample.getSha256Hash());
		
		// remove from queue
		waitingFilesQueue.remove(sample);
		
		// notify listeners 
		listeners.stream().forEach(l -> l.onError(sample, 
				new FileSubmittionFailedException(res.getError())));
	}
		
	private void resumeScheduler()
	{
		logger.fine("PAYLOAD: Starting scheduler");
		task = timer.scheduleWithFixedDelay(
				() -> pollPayloadForReports(), 5, 3, TimeUnit.MINUTES);
	}
	
	private void suspendScheduler()
	{
		logger.fine("PAYLOAD: suspending scheduler");
		task.cancel(false);
	}
	
	//
	// EngineController interface
	//
	@Override
	public boolean getDataOnFile(SampleFile sample) throws FileSubmittionFailedException{
		scanFile(sample);
		
		// Proccessing is not finished, until we get results from payload. 
		return false; 
	}
	
	@Override
	public ControllerData getControllerData(){
		return data;
	}
	
	@Override
	public void shutdownController()
	{
		logger.fine("Shutting down payload controller");
		if (task != null) task.cancel(true);
		Utils.shutdownExecutor(timer, 10);
	}

	public static void main(String[] args) throws Exception
	{
		
		//System.setOut(new PrintStream(new FileOutputStream("stamlog.txt")));
		Utils.setLoggerLevels(true);
		
		// load a report file and test indexing into elastic
		Path dir = Paths.get("C:\\Users\\Assaf\\Desktop\\TestReports");
		//Path dir = Paths.get("C:\\Users\\Assaf\\Desktop\\Verint\\TestFiles\\Results\\damaged\\json");
		//Path dir = Paths.get("C:\\Users\\Assaf\\Desktop\\Verint\\TestFiles\\Results\\benign\\json");
		
		//Path report = dir.resolve("fb2b629c4d491770f6702ec661227e4b58d0ca84d5ec43c46d5aaa0d286749dc.json");
		//Path report = dir.resolve("92406866bcdbe3df7193f0ecca2aca0b2af5dd158fb360c5f47cdc2078b31ab6.json");
		//Path report = dir.resolve("7z1505-x64.exe--6abaf04e44c87bd109df7485eb67a2d69a2e3e6e6deb9df59e5e707176c69449.json");
		//Path report = dir.resolve("0c0bdbe69ff35b2d9c8be1ffb670676dcb6ec9aa98718e13b853640938f86134.json");
		
		//Path report = dir.resolve("report.json");
		List<Path> files = Files.walk(dir)
				.filter(Files::isRegularFile)
				.collect(Collectors.toList());
//			logger.fine("Existing files in dir: " + files.size());
		
		files.forEach((report)-> {
		System.out.println("Hanlding report: " + report.toString());
		String content = "";
		try {
			content = new String(Files.readAllBytes(report));
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println("read report. size: " + content.length());
		if (content.length() == 0) return;
		
		// parse to json
		JsonNode node = new JsonNode(content);
		JSONObject json = node.getObject();
		
		System.out.println("Report length: " + json.toString().length());
		PayloadJsonNormalizer.reduceReportToOurNeeds(json);
		System.out.println("reduced: " + json.toString().length());
		
		PayloadJsonNormalizer.normalize(json);
		
		System.out.println("normalized Report length: " + json.toString().length());
		
//		Path toSave = dir.resolve("results/result.json");
//		try {
//			Files.write(toSave, json.toString().getBytes());
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		
		// index sample
		SampleFile sample = new SampleFile(Paths.get("config/intelligence.yml"));
		System.out.println("Indexing dummy sample");
		ESHandler.getInstance().indexSampleDoc(sample);
		
		// index report
		System.out.println("Indexing big report");
		ESHandler.getInstance().indexPayloadReport(sample, json);
		
		System.out.println("HEY HEY");
		});
	}
}
