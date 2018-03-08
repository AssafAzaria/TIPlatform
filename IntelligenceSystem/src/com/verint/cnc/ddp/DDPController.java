package com.verint.cnc.ddp;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.input.TailerListenerAdapter;

import com.verint.cnc.main.LogFileTailer;
import com.verint.exceptions.FileSubmittionFailedException;
import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;
import com.verint.utils.Utils;

/**
 * A controller that monitors ddp, to know when a file has been
 * completed processing, and transferred.
 * 
 * need to run rmiregistry -J-Djava.class.path=.:DDPController.XXX.jar &
 * @author Assaf Azaria
 */ 
public class DDPController implements DDPRemote {
	// Holds files that were created and waiting for processing
	private ConcurrentMap<String, SubmittedFile> createdFilesMap = new ConcurrentHashMap<>();
	
	// Holds all files that were transferred
	private ConcurrentMap<String, SubmittedFile> transferredFilesMap = new ConcurrentHashMap<>();

	private Logger logger = ErrorLogger.getInstance().getLogger();
	private ExecutorService executor = Executors.newCachedThreadPool();
	
	// The delay between tailer log file checks. The smallr it is, the more 
	// accurate we are, but the cpu will work harder
	private int tailerDelayMillis = 500;
	
	public DDPController() {
		Utils.makeSureExecutorGetsShutdownOnExit(executor, 5);
		
		// Watch the pcaps dir for new files
		logger.info("Starting to watch on pcaps dir");
		executor.submit(() -> watchDirForNewFiles(Utils.getPath(Config.getDDPFilesPath())));
		
		// run the tailer - watching the log file, to know when transfer occured
		executor.submit(tailer);
	}

	// Wait for transfer (transfers:0 line in clearsky.0 log)
	private LogFileTailer tailer = new LogFileTailer(Paths.get(Config.getDDPLogFile()), 
			tailerDelayMillis, new TailerListenerAdapter(){
		@Override
		public void handle(String line) {
			if (createdFilesMap.size() == 0) return;
			
			// We get this only when the pattern was matched. 
			logger.fine("************************* found transfers:0 pattern in log file");
			
			// move all deleted files to transferred  
			createdFilesMap.entrySet()
				.stream()
				.filter(f -> f.getValue().getState() == State.DELETED &&
						wasTheFileReallyTransferred(f.getValue()))
			    .forEach(f -> {
			    	createdFilesMap.remove(f.getKey(), f.getValue());
			    	transferredFilesMap.put(f.getKey(), f.getValue());
			     });
			
			logger.finer("After: Created size: " + createdFilesMap.size());
			logger.finer("Transferred size: " + transferredFilesMap.size());
			
		}
		public void handle(Exception e) {
			logger.severe("tailer problem: " + e.getMessage());
			logger.log(Level.FINE, "", e);
		};
	});

	// In order to know if a file was transferred, we check that it was a. deleted
	// and only then b. "transfers: 0" appeared in log file. The log file tailer uses a delay
	// to poll the file, and hence we can sometimes get an event from log file, that actually
	// happened BEFORE the file deletion. To avoid that, we check the time of the tail event.
	private boolean wasTheFileReallyTransferred(SubmittedFile file)
	{
		return Duration.between(file.getTimeStamp(), Instant.now()).toMillis() >= tailerDelayMillis; 
	}
	
	// Watches the given dir for new files and updates maps
	@SuppressWarnings("unchecked")
	private void watchDirForNewFiles(Path dir) {
		try (WatchService watcher = FileSystems.getDefault().newWatchService()) {
			// register for events on new files
			WatchKey key = dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE);
			logger.info("Starting to watch on dir: " + dir);
			
			while (true) {
				try {
					// wait for key to be signaled
					key = watcher.take();
				} catch (InterruptedException x) {
					// Executor shuts down by interrupting
					logger.fine("watch dir (inotify) interrupted... shutting down");
					break;
				}

				for (WatchEvent<?> event : key.pollEvents()) {
					// an OVERFLOW event can occur, if events are lost or
					// discarded.
					if (event.kind() == OVERFLOW) {
						continue;
					}

					WatchEvent.Kind<?> kind = event.kind();

					// The filename is the context of the event.
					Path newPath = ((WatchEvent<Path>) event).context();
					logger.info("Event kind: " + event.kind().name() + " file: " + newPath);

					if (kind == ENTRY_CREATE) {
						SubmittedFile file = new SubmittedFile(newPath);
						file.setState(State.CREATED);
						createdFilesMap.put(newPath.getFileName().toString(), 
								file);
					}
					else if (kind == ENTRY_DELETE)
					{
						// Intermediate stage, before it is transferred.
						SubmittedFile file = createdFilesMap.get(newPath.getFileName().toString());
						if (file != null){
							file.setState(State.DELETED);
							file.setTimeStamp(Instant.now());
						}
					}
				}

				// Reset the key -- this step is critical if you want to
				// receive further watch events. If the key is no longer valid,
				// the directory is inaccessible so exit the loop.
				if (!key.reset()) {
					logger.severe("watch service problem. reset key failed");
					break;
				}
			}
		} catch (IOException e) {
			logger.severe("watch service problem: " + e.getMessage());
			logger.log(Level.FINE, "", e);
		}

		// finished - either by exception, or by key reset.
	}

	/**
	 * Remote method - check whether the given file was transferred
	 * 
	 * @return true - when the file has been transferred 
	 * false - if the file is unknown, or after 10 attempts to find it.
	 */
	@Override
	public boolean isFileTransferred(String fileName) throws RemoteException, 
		FileSubmittionFailedException {
		logger.finer("********************* Rmi call for file: " + fileName);
		
		// 10 attempts before a false answer
		for(int i = 1; i <= 10; i++) {
			// File has allready been transferred
			if (transferredFilesMap.containsKey(fileName)){
				logger.fine("file " + fileName + " was transferred. returning");
				// purge map
				transferredFilesMap.remove(fileName);
				return true;
			}
			
			// File was created but not yet transferred. Wait for a couple of 
			// seconds and check again.
			if (createdFilesMap.containsKey(fileName)){
				logger.fine("file " + fileName + " is waiting for transfer. waiting: " + i);
				try {
					TimeUnit.SECONDS.sleep(5);
				} catch (InterruptedException e) {
					logger.finer("interrupted on sleep. ok");
				}
				continue;
			}
			else {
				// unknown file
				logger.severe("file" + fileName + " doesn't exist!");
				return false;
			}
		}
		
		// 10 Attempts were failed.
		return false;
	}

	// rmiregistry -J-Djava.class.path=.:DDPController1.1.1.jar &
	public static void main(String[] args) {
		Logger logger = ErrorLogger.getInstance().getLogger();
		Utils.setLoggerLevels(true);
		
		try {
			// Register the object in RMI.
			DDPController obj = new DDPController();
			DDPRemote stub = (DDPRemote) UnicastRemoteObject.exportObject(obj, 0);
			
			// Bind the remote object's stub in the registry
			Registry registry = LocateRegistry.getRegistry();
			logger.info("registry found. stub: " + stub.getClass().getName());
			registry.rebind("DDPRemote", stub);

			logger.info("Server ready");
		} catch (Exception e) {
			logger.severe("Server exception: " + e.toString());
			e.printStackTrace();
			System.exit(0);
		}
	}
	
	
	
}
