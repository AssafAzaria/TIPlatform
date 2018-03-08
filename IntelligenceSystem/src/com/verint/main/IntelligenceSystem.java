package com.verint.main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.tika.mime.MediaType;

import com.verint.es.ESHandler;
import com.verint.exceptions.FileSubmittionFailedException;
import com.verint.moloch.MolochController;
import com.verint.utils.Config;
import com.verint.utils.DailyReportGenerator;
import com.verint.utils.ErrorLogger;
import com.verint.utils.Utils;


// MAYBE THE EXPECTED TESTS ON THE SAMPLE IS THE PROBLEM. IT SHOULD BE ONLY THOSE WHO
// ARE SUPPORTED TO BEGIN WITH. maybe.
/**
 * @author Assaf Azaria
 */
public class IntelligenceSystem {
	private Logger logger = ErrorLogger.getInstance().getLogger();
	
	private Path filesPath, savePath;
	
	// incoming files queue
	private BlockingQueue<SampleFile> filesQueue = new ArrayBlockingQueue<>(80);
	
	// queue for files waiting for asynchronous response
	private ConcurrentMap<String, SampleFile> waitingQueue = new ConcurrentHashMap<>();
	
	private EnumSet<CheckType> expectedTests = EnumSet.noneOf(CheckType.class);
	
	private ExecutorService executor; 
	private DirWatcher dirWatcher;
	private volatile boolean stopRequested = false;

	// The list of controllers to run
	private List<EngineController> controllers;
	
	// Writes summary report on samples
	private DailyReportGenerator reportGen = new DailyReportGenerator();
	
	
	// TODO: some order here.
	private MolochController moloch = new MolochController();
	
	public IntelligenceSystem() {
		Utils.setLoggerLevels(true);
		addShutdownHook();
		
		filesPath = Utils.getPath(Config.getFilesPath());
		savePath = Utils.getPath(Config.getSavePath());

		loadControllers();
		
		// Start watching on files path
		executor = Executors.newCachedThreadPool();
		executor.submit(dirWatcher = new DirWatcher(filesPath, filesQueue));

		runControllers();
	}
	
	private void loadControllers()
	{
		// load all needed controllers
		controllers = ControllersFactroy.getInstance().getControllers();
		
		// add listeners to relevant controllers
		final EngineListener listener = new EngineEventsHandler();
		
		controllers.forEach(c -> {
			expectedTests.add(c.getControllerData().getCheckType());
			if (c instanceof AsyncEngineController)
				((AsyncEngineController)c).addListener(listener);
		});
		
		
	}
	
	private void runControllers() 
	{	
		while (!stopRequested){
			// Submit files from queue to controllers
			try {
				SampleFile sample = filesQueue.take();
				logger.info("Main: Submitting " + sample.getPath() + " to controllers");
				
				sample.setExpectedTests(expectedTests);
				processSample(sample);
			} catch (InterruptedException e) {
				logger.fine("Main: FilesQueue interrupted on take... " + e.getMessage());
			} catch (FileSubmittionFailedException e) {
				logger.severe("Main: Submittion failed: " + e.getMessage());
				logger.finest("----------------------------------------");
			}catch (Exception e) {
				logger.severe("Main: Submittion failed: " + e.getMessage());
				logger.finest("----------------------------------------");
			}
		}
		logger.info("Main: Exiting... bye bye");
		
		shutdownSystem();
	}
	
	// process a sample through available controllers
	private void processSample(SampleFile sample)
	{
		// index the sample base document. We are not using a separate thread,
		// to ensure the base doc will be there, before other data
		ESHandler.getInstance().indexSampleDoc(sample);
		
		// Move file to waiting queue
		waitingQueue.put(sample.getEsId(), sample);
		
		// Run all controllers in parralel. 
		controllers.parallelStream().forEach((c) -> {
			CheckType cType = c.getControllerData().getCheckType();
			MediaType mimeType = sample.getMimeType();
			boolean supported = c.getControllerData().isTypeSupported(mimeType);
			logger.fine("Main: Controller:" + cType + 
					    " type: " + mimeType + 
					    " Supported? " + supported);
			
			if (supported){
				runControllerOnSample(c, sample);
			}	
			else{ 
				// mark the test as 'not supported' on the sample
				sample.addPerformedTest(new PerformedTest.Builder(cType).
						supported(false).build());
			}
						
		});
		
		
		 
	}
	
	private void runControllerOnSample(EngineController c, SampleFile sample)
	{
		boolean opCompleted = true;
		PerformedTest.Builder test = 
				new PerformedTest.Builder(c.getControllerData().getCheckType())
				.supported(true)
				.success(true);
		
		
		try{
			opCompleted = c.getDataOnFile(sample);
		}catch(FileSubmittionFailedException e){
			// TODO: something with failure
			// maybe pass on? or at least log?
			test.success(false);
			test.description(e.getMessage());
		}
		
		// mark the file if finished 
		if (opCompleted) {
			sample.addPerformedTest(test.build());
			checkIfFinishedAllTests(sample);
		}
	}
	
	private class EngineEventsHandler implements EngineListener{
		@Override
		public void onResponse(Response res){
			logger.info("Main: Got response from payload. pcap: " + res.getPcap());
			
			// a. set the sample status 
			PerformedTest.Builder test = new PerformedTest.Builder(res.getCheckType());
			res.getSample().addPerformedTest(test.success(true).build());
			
			// if we have a pcap - pass it through the system
			res.getPcap().ifPresent(pcap -> {
				processNewSample(res.getSample(), pcap);
			});
			
			checkIfFinishedAllTests(res.getSample());
		}

		@Override
		public void onError(SampleFile sample, FileSubmittionFailedException e)
		{
			logger.severe("Main: payload error: " + e.getMessage());
			// TODO: a. set the sample status
			// b. remove from waiting queue (if finished)
		}
		
		void processNewSample(SampleFile parentSample, Path path)
		{
			logger.info("Main: Submitting downloaded PCAP to the system");
			
			// new sample with same context.
			Context ctx = parentSample.getContext();
			ctx.incRunningStep();
			
			FileDetails params = new FileDetails();
			params.setFileSource("sandbox");
			SampleFile newSample = new SampleFile(path, Utils.PCAP_MIME, ctx, params);
			
			// release events thread
			executor.submit(() -> processSample(newSample));
		}
		
	}

	private void checkIfFinishedAllTests(SampleFile sample)
	{
		if (sample.isProcessingFinished()){
			logger.info("Main: -----Finished processing of sample: " + sample.getPath());
			waitingQueue.remove(sample.getEsId(), sample);
			
			// If it is a pcap, we store it on Moloch
			if (sample.isPcap() && Config.runMolochController()){
				logger.info("Main: Sending " + sample.getPath() + " to moloch for storage");
				moloch.submitPcapToMoloch(sample.getPath());
			}
			
			reportGen.addLineToReport(sample);
			
			// Move finished file to storage
			executor.submit(() -> moveFileToStorage(sample.getPath()));
		}
	}
	
	// Moves completed files to storage
	private void moveFileToStorage(Path source) {
		try {
			Path dest = savePath.resolve(source.getFileName());
			Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			logger.log(Level.INFO, "file copy failed", e);
		}
	}

	private void addShutdownHook(){
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			shutdownSystem();
		}));
	}
	
	// Shutdown the thread pool and exit.
	private void shutdownSystem() {
		logger.info("----------------------------------"); 
		
		logger.info("Shutting down system. Please wait"); 
		stopRequested = true;
		
		// disables new tasks from being submitted
		executor.shutdown(); 
				
		// shutdown dir watcher
		if (dirWatcher != null)
			 dirWatcher.shutdown();
		
		// shutdown controllers
		controllers.parallelStream().forEach(c -> c.shutdownController());
				
		try {
			// Wait a while for existing tasks to terminate
			if (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
				executor.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!executor.awaitTermination(1, TimeUnit.MINUTES))
					logger.info("Warning: thread pool did not terminate");
			}
		} catch (InterruptedException ie) {
			// (Re-)Cancel if current thread also interrupted
			executor.shutdownNow();
			// Preserve interrupt status
			Thread.currentThread().interrupt();
		}
	}
	
	public static void main(String[] args) {
		new IntelligenceSystem();
	}
	
	
	
}
