package com.verint.cnc.cnc;

import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.io.input.TailerListenerAdapter;

import com.verint.cnc.main.LogFileTailer;
import com.verint.exceptions.DaoException;
import com.verint.exceptions.FileSubmittionFailedException;
import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;
import com.verint.utils.SystemCommandCaller;
import com.verint.utils.Utils;

/**
 * Controller for CNC data. Can be accessed remotely, to get 
 * cnc data on a given pcap file.
 * 
 * need to run rmiregistry -J-Djava.class.path=.:CNCController.XXX.jar &
 * @author Assaf Azaria
 */
public class CNCController implements CNCRemote {
	private static final String PROCESS_NAME_TO_FLUSH = "dataaggregator";
	private static final int FLUSH_ALL_KEYS_SIGNAL = 40;
	
	private Logger logger = ErrorLogger.getInstance().getLogger();
	private CNCDao dao = new CNCDao();
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	
	// A condition we use to signal when relevant log file entry was found
	private final Lock lock = new ReentrantLock();
	private final Condition waitingForLog = lock.newCondition();
	
	public CNCController() {
		// start tailing log file
		executor.submit(tailer);
	}
	
	/**
	 * Remote method. Get's CNC data on a given pcap.
	 * 
	 * This function should work on one file at a time, 
     * hence the synchronization
	 */
	@Override
	public synchronized CNCData getCNCData(String pcapName) throws RemoteException, 
		FileSubmittionFailedException {
		try{
			// First, tell CNC (DAP) to flush it's data to dsr table, ending the session
			flushCNC();
			
			// Now we wait for it to finish flushing, monitoring the log file.
			logger.info("waiting for log file");
			waitForTransfer();
			
			logger.info("getting dsr from table");
			List<DSRData> dsrDataList = dao.getDsrsInfo(pcapName);
			CNCData allData = new CNCData(pcapName, dsrDataList);
			
			// We wait for LAP to finish reading by checking the last dsr row it read.
			logger.info("waiting for lap to finish reading");
			waitForLapToFinishReading(dsrDataList);
			
			// wait some more - (to make sure lap finished writing all alerts)
			logger.info("waiting for lap to finish writing");
			sleep(5);
			
			// Get all scores
			logger.info("getting dsr scores");
			
			dsrDataList.parallelStream().forEach(dsr -> setDsrAlert(dsr));
			
			logger.info("finished processing. returning call");
			return allData;
		}
		catch(Exception e)
		{
			logger.info("CNC exception, see log. msg: " + e.getMessage());
			logger.log(Level.SEVERE, "", e);
			
			// Pass the exception to client
			throw new FileSubmittionFailedException(e.getMessage(), e);
		}
		
	}
	
	// Sets the alert on a given dsr
	private void setDsrAlert(DSRData dsr) throws DaoException
	{
		Optional<Alert> ret = dao.getDsrScore(dsr);
		if (!ret.isPresent()) {
			// There should be an alert for each dsr, after lowering the treshold
			logger.fine("Dsr: " + dsr.getRowId() + " has no alert!");
			return;
		}
	
		Alert alert = ret.get();
		dsr.setScore(alert.getScore());
		
		// Check for white list - if 'hide_mask' != -1
		dsr.setInWhiteList(!alert.getHideMask().equals("-1"));
		
		// Check for black list
		dsr.setInBlackList(dao.isInBlackList(alert.getHostName(), alert.getServerIp()));
		
	}
	
	// Flush the cnc, closing the session artificialy
	private void flushCNC()
	{
		// We tell CNC to flush it's data using kill -40 command
		String command = "killall";
		List<String> args = Arrays.asList("-" + FLUSH_ALL_KEYS_SIGNAL, PROCESS_NAME_TO_FLUSH);
		SystemCommandCaller.invoke(command, args);
		
	}
	
	// Wait for a line to show up in the log file, or 30 seconds.
	private void waitForTransfer() throws TimeoutException
	{
		lock.lock();
		try {
			// Wait for it to finish, max timeout 45 secs
			if (!waitingForLog.await(45, TimeUnit.SECONDS)){
				throw new TimeoutException("Dap Log file tailer timed-out");
			}
		} catch (InterruptedException e) {
			logger.finer("interrupted. fine");
		}finally{
			lock.unlock();
		}
		
		logger.fine("tailer finished");
	}
	
	// Tails the log file to know if dap finished processing
	private LogFileTailer tailer = new LogFileTailer(Paths.get(Config.getDapLogFile()), 
			new TailerListenerAdapter(){
		@Override
		public void handle(String line) {
			// We get this only when the pattern was matched. 
			// logger.fine("************************* found transfers:0 pattern in log file");
			
			// notify waiting threads, if any
			signalWaitingThreads(lock, waitingForLog);
		}
	});
	
	// Wake up a thread waiting on the given condition
	private void signalWaitingThreads(Lock lock, Condition condition)
	{
		lock.lock();
		try{ 
			condition.signal();
		}finally{
			lock.unlock();
		}
	}
	
	private void waitForLapToFinishReading(List<DSRData> dsrDataList) throws DaoException
	{
		// If there are 0 dsrs, then it will wait forever
		if (dsrDataList.size() == 0) return; // Excep?
		
		int maxLapRow = 0;
		int maxDsrRow = getHighestDsrRowId(dsrDataList);
		logger.finer("MaxDsrRow: " + maxDsrRow);
		do{
			sleep(2);
			maxLapRow = dao.getLapLastReadRow();
			logger.finer("MaxLapRow: " + maxLapRow);
		}while(maxLapRow < maxDsrRow);
	}
	
	
	// Get the highest row id from the given list of dsrs
	private int getHighestDsrRowId(List<DSRData> dsrDataList)
	{
		if (dsrDataList == null || dsrDataList.size() == 0) return 0;
		
		return dsrDataList.stream()
			.sorted((dsr1, dsr2) -> dsr2.getRowId() - dsr1.getRowId())
			.findFirst().get().getRowId();
	
	}
	
	// just to avoid the exception
	private void sleep(int seconds)
	{
		try {
			TimeUnit.SECONDS.sleep(seconds);
		} catch (InterruptedException e) {
			logger.finer("interrupted on sleep, fine");
		}
	}
	
	public static void main(String[] args) {
		// config logger
		Logger logger = ErrorLogger.getInstance().getLogger();
		Utils.setLoggerLevels(true);
			
		try {
			// Register the object in RMI.
			CNCController obj = new CNCController();
			CNCRemote stub = (CNCRemote) UnicastRemoteObject.exportObject(obj, 0);
			
			// Bind the remote object's stub in the registry
			Registry registry = LocateRegistry.getRegistry();
			logger.fine("registry found. stub: " + stub.getClass().getName());
			registry.rebind("CNCRemote", stub);

			logger.fine("Server ready");
		} catch (Exception e) {
			logger.severe("Server exception: " + e.toString());
			logger.log(Level.SEVERE, "", e);
			
			System.exit(0);
		}
	}
}
