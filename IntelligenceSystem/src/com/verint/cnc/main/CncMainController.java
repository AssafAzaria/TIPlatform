package com.verint.cnc.main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.tika.mime.MediaType;

import com.verint.cnc.cnc.CNCData;
import com.verint.cnc.cnc.CNCRemote;
import com.verint.cnc.ddp.DDPRemote;
import com.verint.es.ESHandler;
import com.verint.exceptions.ControllerLoadFailure;
import com.verint.exceptions.FileSubmittionFailedException;
import com.verint.main.CheckType;
import com.verint.main.ControllerData;
import com.verint.main.EngineController;
import com.verint.main.SampleFile;
import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;
import com.verint.utils.Utils;

/** 
 * Allows getting CNC data on pcaps, and saves it to ES.
 * 
 * Each pcap is first uploaded to DDP machine, and after it finishes processing,
 * the code goes to CNC machine(s), to get dsrs and scores.
 *
 * @author Assaf Azaria
 */
public class CncMainController implements EngineController {
	private Logger logger = ErrorLogger.getInstance().getLogger();
	private SCPUploader uploader;
	private ControllerData data; 
	
	private List<CncMachine> cncMachines;
	private DDPRemote ddpStub;
	private ExecutorService executor = Executors.newCachedThreadPool();

	public CncMainController() throws ControllerLoadFailure{
		data = createControllerData();
		
		uploader = new SCPUploader();

		// init remote objects
		getRemoteObjectStubs();
	}

	// Here we decide what mime types are supported by this controller, among
	// other things
	private ControllerData createControllerData()
	{
		// Currently support all pcap files only
		Predicate<MediaType> isSupported = 
				(type) -> type.equals(Utils.PCAP_MIME);
		return new ControllerData(CheckType.CNC, isSupported);
	}
	
	/**
	 * Submits a file for processing.
	 * @param sample the sample pcap
	 * @return All cnc data related to this sample
	 * @throws FileSubmittionFailedException
	 */
	// Note: Because of uploader, you can't call this function in parrallel. 
	// hence the synchronization
	public synchronized List<CNCData> submitFileToCnc(SampleFile sample) throws FileSubmittionFailedException {
		logger.fine("CNC: *********** submitting " + sample.getPath().getFileName() + " to cnc");

		List<CNCData> cncDataList = new ArrayList<>();
		Path file = sample.getPath();

		// first, copy the file to ddp machine.
		logger.info("CNC: Uploading file " + file.getFileName().toString() + " to ddp machine");
		try {
			uploader.uploadFile(file);
		} catch (IOException e) {
			String msg = "CNC: Upload problem: " + e.getMessage();
			logger.warning(msg);
			// because the ddp deletes files almost immediately, we get an exception
			// here - and practically ignore it
		}
		
		// wait for file to be transferred to cnc machine
		logger.info("CNC: Waiting for ddp to transfer file");
		try {
			boolean response = ddpStub.isFileTransferred(file.getFileName().toString());
			if (!response) {
				String msg = "CNC: file was not transferred from ddp. Check ddp machine";
				logger.severe(msg);
				throw new FileSubmittionFailedException(msg);
			}
		} catch (RemoteException e) {
			logger.severe("CNC: RMI problem: " + e.getMessage());
			logger.log(Level.INFO, "", e);
			throw new FileSubmittionFailedException(e.getMessage(), e);
		}
		
		// get reports from cnc machines
		cncDataList = getReportsFromCncMachines(sample);
		logger.fine("CNC: *********** finished " + sample.getPath().getFileName());

		return cncDataList;
	}

	private List<CNCData> getReportsFromCncMachines(SampleFile sample) {
		// use the count-down-latch to wait for all tasks to finish
		CountDownLatch latch = new CountDownLatch(cncMachines.size());

		List<CNCData> cncDataList = new ArrayList<>();
		for (CncMachine cnc : cncMachines) {
			executor.submit(() -> {
				try{
					cncDataList.add(getCncData(cnc, sample));
					logger.fine("CNC: Got cnc data for machine: " + cnc.getHostName());
				}
				catch(FileSubmittionFailedException e)
				{
					logger.severe("CNC: Failed to get cnc data from machine: " + cnc.getHostName() + " msg: " + 
							e.getMessage());
				}
				finally{
					latch.countDown();
				}
			});
		}

		// Wait for all cnc calling threads to finish
		try {
			if (!latch.await(25, TimeUnit.SECONDS))
			{
				// We don't throw exception here, to not lose data
				logger.severe("CNC: Not all cnc machines returned data. Reached timeout");
			}
		} catch (InterruptedException e) {
			logger.finer("countdownlatch interrupted... ");
		}

		return cncDataList;
	}

	// Get data from the given cnc machine on the given sample
	// TS
	private CNCData getCncData(CncMachine cncMachine, SampleFile sample) {
		final Path file = sample.getPath();
		logger.info("CNC: Getting data from cnc machine " + cncMachine.getHostName());
		try {
			CNCData cncData = cncMachine.getRemoteStub().getCNCData(file.getFileName().toString());
			cncData.setCncMachine(cncMachine);

			// Write data to ES.
			ESHandler.getInstance().indexCncDoc(sample, cncData);

			return cncData;
		}
		catch (RemoteException e) {
			String msg = "CNC: " + cncMachine.getHostName() + "Remote exception, see log. msg: " + e.getMessage();
			logger.info(msg);
			logger.log(Level.SEVERE, "", e);
			throw new FileSubmittionFailedException(msg);
		}
	}

	// TODO: 
	// initialize remote objects stubs
	private final void getRemoteObjectStubs() throws ControllerLoadFailure {
		
		try {
			Registry registry = LocateRegistry.getRegistry(Config.getDDPHost());
			
			ddpStub = (DDPRemote) registry.lookup("DDPRemote");
			logger.fine("CNC: loaded ddp stub");
		} catch (RemoteException | NotBoundException e) {
			logger.log(Level.SEVERE, "CNC: Cannot get ddp stub, controller aborting", e);
			// controller cannot work without ddp.
			throw new ControllerLoadFailure(e);
		}
		
			cncMachines = Config.getCNCHosts();
			for (CncMachine c : cncMachines) {
				logger.fine("CNC: loading cnc stub for " + c.getHostName());
				try{
					Registry registry = LocateRegistry.getRegistry(c.getHostName());
					c.setRemoteStub((CNCRemote) registry.lookup("CNCRemote"));
				} catch (RemoteException | NotBoundException e) {
					logger.log(Level.SEVERE, "CNC: Cannot get rmi stub for cnc machine: " + c.getHostName(), 
							e);
				}
			}

		
	}

	@Override
	public boolean getDataOnFile(SampleFile sample) throws FileSubmittionFailedException {
		submitFileToCnc(sample);
		return true;
		
	}
	
	@Override
	public ControllerData getControllerData()
	{
		return data;
	}

	@Override
	public void shutdownController()
	{
		logger.fine("Shutting down cnc controller");
		Utils.shutdownExecutor(executor, 30);
	}
	

	public static void main(String[] args) {
		// Logger logger = ErrorLogger.getInstance().getLogger();
		Utils.setLoggerLevels(true);

		CncMainController c = new CncMainController();
		List<Path> filesInFolder = null;
		try {
			filesInFolder = Files.walk(Paths.get("pcapfiles")).filter(Files::isRegularFile)
					.collect(Collectors.toList());
			System.out.println("Loaded " + filesInFolder.size() + " files");
		} catch (IOException e) {
			e.printStackTrace();
		}

		List<CNCData> results = new LinkedList<>();
		filesInFolder.stream().forEach((file) -> {
			SampleFile sFile = new SampleFile(file);
			ESHandler.getInstance().indexSampleDoc(sFile);

			c.submitFileToCnc(sFile).stream().forEach((data) -> {
				results.add(data);
			});

		});
	}

	
	
}
