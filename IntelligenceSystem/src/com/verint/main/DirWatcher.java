package com.verint.main;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;

/**
 * Watches a folder for new files, loads them to queue
 * @author Assaf
 *
 */
public class DirWatcher implements Runnable
{
	private Path filesPath;
	private BlockingQueue<SampleFile> filesQueue;
	private Logger logger = ErrorLogger.getInstance().getLogger();
	private boolean loadCurrent = true;
	private volatile boolean stopFlag = false;
	private WatchService watcher; 
	private FileDetails baseParams = new FileDetails();
	
	/**
	 * Create a new dir watcher
	 * @param filesPath the path to watch
	 * @param filesQueue the files queue to load the files to
	 */
	public DirWatcher(Path filesPath, BlockingQueue<SampleFile> filesQueue)
	{
		this(filesPath, filesQueue, true);
	}
	
	/**
	 * Create a new dir watcher
	 * @param filesPath the path to watch
	 * @param filesQueue the files queue to load the files to
	 * @param loadCurrent whether to load current files on dir, or only new ones
	 */
	public DirWatcher(Path filesPath, BlockingQueue<SampleFile> filesQueue, boolean loadCurrent)
	{
		this.filesQueue = filesQueue;
		
		// the dir we watch for files to submit
		this.filesPath = filesPath;
		
		this.loadCurrent = loadCurrent;
	}
	
	// Watches the given dir for new files and updates the queue
	@SuppressWarnings("unchecked")
	private void watchDirForNewFiles() {
		// read all current files from dir before watching for new ones
		if (loadCurrent)
			loadCurrentFilesInDir();
		try {
			watcher = FileSystems.getDefault().newWatchService();
			
			// register for events on new files
			WatchKey key = filesPath.register(watcher, ENTRY_CREATE);
			logger.info("Main: Starting to watch on dir: " + filesPath);
			while (!stopFlag) {
				try {
					// wait for key to be signaled
					key = watcher.take();
					
				} catch (InterruptedException x) {
					logger.fine("Main: watching dir interrupted");
					continue;
				}

				for (WatchEvent<?> event : key.pollEvents()) {
					// an OVERFLOW event can occur, if events are lost or discarded.
					if (event.kind() == OVERFLOW) {
						continue;
					}

					// The filename is the context of the event.
					Path newPath = ((WatchEvent<Path>) event).context();
					logger.info("Main: New path created: " + newPath);

					// Without this, it doesn't recognize dirs.
					Path resolved = filesPath.resolve(newPath);

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
		} 
		catch (IOException e) {
			logger.severe("watch service problem: " + e.getMessage());
			logger.log(Level.FINE, "", e);
		}
		catch (ClosedWatchServiceException e){
			// this is expected on shutdown
			logger.fine("watch service was closed");
		}

		// finished - either by exception, or by key reset.
		logger.info("Dir watcher is down");
	}

	private void processNewFile(Path newPath) throws IOException {
		if (Files.isDirectory(newPath)) {
			logger.finer("Main: dir: " + newPath);

			// load params file
			FileDetails params = loadParamsFile(newPath);
			
			// load all files recursively
			List<Path> files = Files.walk(newPath)
					.filter(Files::isRegularFile)
					.filter(f -> !f.endsWith(".txt")) // omit txt files
					.collect(Collectors.toList());
			logger.fine("Main: Files: " + files.size());

			files.stream()
				 .forEach(f -> addToQueue(f, params));
		} else {
			addToQueue(newPath);
		}
	}

	private void addToQueue(Path path) {
		addToQueue(path, baseParams);
		
	}
	
	private void addToQueue(Path path, FileDetails params) {
		logger.fine("Main: adding file to queue: " + path);
		SampleFile sample = new SampleFile(path, params);
		filesQueue.add(sample);
	}
	
	private FileDetails loadParamsFile(Path dirPath)
	{
		// Check if there is a params file
		FileDetails ret = new FileDetails();
		Path paramsPath = dirPath.resolve(Config.getParamsPath());
		if (Files.exists(paramsPath)){
			ret.loadFromFile(paramsPath);
		}
		
		return ret;
	}
	
	private void loadCurrentFilesInDir()
	{
		baseParams = loadParamsFile(filesPath);
		
		try{
			List<Path> files = Files.walk(filesPath)
					.filter(Files::isRegularFile) // omit dirs
					.filter(f -> !f.endsWith(".txt")) // omit txt files
					.collect(Collectors.toList());
			logger.fine("Main: Existing files in dir: " + files.size());
			files.forEach(f -> addToQueue(f));
		}catch(IOException e)
		{
			logger.log(Level.INFO, "problem loading current files in dir: " + e.getMessage());
		}
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
		} catch (IOException e) {
			logger.log(Level.INFO, "waiting for copy finish exception: " + e.getMessage());
		}
	}

	@Override
	public void run()
	{
		watchDirForNewFiles();
	}

	public void shutdown()
	{
		stopFlag= true;
		
		try {
			watcher.close();
		} catch (IOException e) {
			logger.finer("closing watch service: " + e.getMessage());
		}
	}


}
