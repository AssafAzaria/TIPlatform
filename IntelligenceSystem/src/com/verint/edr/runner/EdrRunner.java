package com.verint.edr.runner;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.verint.edr.controller.EDRController;
import com.verint.es.ESHandler;
import com.verint.exceptions.FileSubmittionFailedException;
import com.verint.main.SampleFile;
import com.verint.utils.ErrorLogger;
import com.verint.utils.Utils;

// Main program to load files and get EDR report on them.
// Watches "files_to_test" dir, and submits every file/dir in there.
// Files are saved on "submitted_files" directory
public class EdrRunner {
	public static final String FILES_PATH = "files_to_test";
	public static final String SAVE_PATH = "submitted_files";
	
	private Path filesPath, savePath;
	private EDRController edrController;
	private Logger logger = ErrorLogger.getInstance().getLogger();
	private BlockingQueue<Path> FilesQueue = new ArrayBlockingQueue<>(50);
	private ExecutorService executor = Executors.newCachedThreadPool(); 
	private volatile boolean stopRequested = false;

	public EdrRunner() {
		Utils.setLoggerLevels(true);
		Utils.makeSureExecutorGetsShutdownOnExit(executor, 10);
		
		// the dir we watch for files to submit
		filesPath = Utils.getPath(FILES_PATH);
		savePath = Utils.getPath(SAVE_PATH);

		// Login to vBox
		//VirtualBoxManager mgr = VBoxUtils.loginToVBox(vboxUrl);

		edrController = new EDRController();

		/// Start watching on dir
		Runnable watchDirTask = () -> watchDirForNewFiles(filesPath);
		executor.submit(watchDirTask);

		// Submit files from queue to controller
		while (!stopRequested)
		{
			try {
				// logger.fine("Files in queue: " + FilesQueue.size());
				Path path = FilesQueue.take();
				logger.info("Submitting " + path + " to edr controller");
				SampleFile sample = new SampleFile(path);
				
				ESHandler.getInstance().indexSampleDoc(sample);
				
				edrController.submitFileToVM(sample);

				executor.submit(() -> moveFileToStorage(path));
			} catch (InterruptedException e) {
				logger.fine("FilesQueue interrupted on take... " + e.getMessage());
			} catch (FileSubmittionFailedException e) {
				logger.severe("Submittion failed: " + e.getMessage());
				logger.finest("----------------------------------------");
			}
		}
		logger.info("Exiting... bye bye");
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

	// Watches the given dir for new files and updates the queue
	@SuppressWarnings("unchecked")
	private void watchDirForNewFiles(Path dir) {
		// read all current files from dir before watching for new ones
		loadCurrentFilesInDir();
		
		try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
			// register for events on new files
			WatchKey key = dir.register(watcher, ENTRY_CREATE);
			logger.info("Starting to watch on dir: " + dir);
			while (true) {
				try {
					// wait for key to be signaled
					key = watcher.take();
				} catch (InterruptedException x) {
					logger.fine("watching dir interrupted on take...");
					continue;
				}

				for (WatchEvent<?> event : key.pollEvents()) {
					// an OVERFLOW event can occur, if events are lost or discarded.
					if (event.kind() == OVERFLOW) {
						continue;
					}

					// The filename is the context of the event.
					Path newPath = ((WatchEvent<Path>) event).context();
					logger.info("New path created: " + newPath);

					// Without this, it doesn't recognize dirs.
					Path resolved = dir.resolve(newPath);

					waitForFileToFinishCopying(resolved);
					processNewFile(resolved);
				}

				// Reset the key -- this step is critical if you want to
				// receive further watch events. If the key is no longer valid,
				// the directory is inaccessible so exit the loop.
				if (!key.reset()) {
					break;
				}
			}
		} catch (IOException e) {
			logger.severe("watch service problem: " + e.getMessage());
			logger.log(Level.FINE, "", e);
		}

		// finished - either by exception, or by key reset.
		shutdownRunner();
	}

	private void processNewFile(Path newPath) throws IOException {
		if (Files.isDirectory(newPath)) {
			logger.finer("dir: " + newPath);

			// load all files recursively
			List<Path> files = Files.walk(newPath)
					.filter(Files::isRegularFile)
					.collect(Collectors.toList());
			logger.fine("Files: " + files.size());

			files.forEach(f -> addToQueue(f));
		} else {
			addToQueue(newPath);
		}
	}

	private void addToQueue(Path path) {
		logger.fine("adding file to queue: " + path);
		FilesQueue.add(path);
	}

	

	// Doesn't work for big files/dirs.
	// Tried everything: Getting last modified time. Renaming. Getting write
	// access. locking. nothing works completely. 
	private void waitForFileToFinishCopying(Path file) {
		try {
			long first, second;
			do {
				first = Files.getLastModifiedTime(file).to(TimeUnit.MICROSECONDS);
				try {
					TimeUnit.SECONDS.sleep(12);
				} catch (InterruptedException e) {
					logger.fine("wait for copy interrupted");
				}
				second = Files.getLastModifiedTime(file).to(TimeUnit.MICROSECONDS);
			} while (first < second);
			//logger.fine("File copy finished");
		} catch (IOException e) {
			logger.log(Level.INFO, "waiting for copy finish exception: " + e.getMessage());
		}
	}
	
	private void loadCurrentFilesInDir()
	{
		try{
			List<Path> files = Files.walk(filesPath)
					.filter(Files::isRegularFile)
					.collect(Collectors.toList());
			logger.fine("Existing files in dir: " + files.size());
			files.forEach(f -> addToQueue(f));
		}catch(IOException e)
		{
			logger.log(Level.INFO, "problem loading current files in dir: " + e.getMessage());
		}
	}

	// Shutdown the thread pool and exit.
	private void shutdownRunner() {
		stopRequested = true;
		
		// Wait for tasks to finish
		executor.shutdown(); // disables new tasks from being submitted
		try {
			// Wait a while for existing tasks to terminate
			if (!executor.awaitTermination(5, TimeUnit.MINUTES)) {
				executor.shutdownNow(); // Cancel currently executing tasks
				// Wait a while for tasks to respond to being cancelled
				if (!executor.awaitTermination(2, TimeUnit.MINUTES))
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
		new EdrRunner();
	}
}
