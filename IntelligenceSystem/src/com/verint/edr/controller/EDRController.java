package com.verint.edr.controller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.tika.mime.MediaType;
import org.virtualbox_5_1.AccessMode;
import org.virtualbox_5_1.DeviceType;
import org.virtualbox_5_1.GuestSessionWaitForFlag;
import org.virtualbox_5_1.IConsole;
import org.virtualbox_5_1.IGuest;
import org.virtualbox_5_1.IGuestProcess;
import org.virtualbox_5_1.IGuestSession;
import org.virtualbox_5_1.IMachine;
import org.virtualbox_5_1.IMedium;
import org.virtualbox_5_1.IProgress;
import org.virtualbox_5_1.ISession;
import org.virtualbox_5_1.ISnapshot;
import org.virtualbox_5_1.IVirtualBox;
import org.virtualbox_5_1.LockType;
import org.virtualbox_5_1.MachineState;
import org.virtualbox_5_1.ProcessCreateFlag;
import org.virtualbox_5_1.ProcessWaitForFlag;
import org.virtualbox_5_1.SessionState;
import org.virtualbox_5_1.VBoxException;
import org.virtualbox_5_1.VirtualBoxManager;

import com.verint.edr.vbox.VBoxUtils;
import com.verint.es.ESHandler;
import com.verint.exceptions.ControllerLoadFailure;
import com.verint.exceptions.FailReason;
import com.verint.exceptions.FileSubmittionFailedException;
import com.verint.iso.IsoCreator;
import com.verint.main.CheckType;
import com.verint.main.ControllerData;
import com.verint.main.EngineController;
import com.verint.main.SampleFile;
import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;
import com.verint.utils.Utils;

/**
 * A controller that uses a sandbox to get EDR data on executables, and store
 * the info on ElasticSearch.
 * 
 * It is essential to run \bin\  -t 500. it creates the VBOX WebService
 * with longer session timeout than the default 5 minutes.
 * 
 * @author Assaf Azaria
 *
 */
public class EDRController implements EngineController
{
	private static final String CLIENT_VM_NAME = "Windows 7 (64)";
	private static final String CLIENT_VM_SNAPSHOT = "EDR_102_15";

	private static final String SERVER_VM_NAME = "Ubuntu 16 (x64)";
	private static final String SERVER_VM_SNAPSHOT = "Snapshot 13";

	private static final String CLIENT_VM_USER_NAME = "User01";
	private static final String CLIENT_VM_PASSWORD = "123qwe!@#";

	private static final String SERVER_VM_USER_NAME = "user01";
	private static final String SERVER_VM_PASSWORD = "123qwe!@#";

	private static final Path GUEST_DVD = Paths.get("E:\\");
	private static final Path EDR_PROCESS = Paths.get("C:\\Program Files (x86)\\EndpointMonitor\\run_edr_service.bat");

	private ControllerData data;

	private VirtualBoxManager mgr;
	private IVirtualBox vbox;
	private Logger logger = ErrorLogger.getInstance().getLogger();
	private IMachine ubuntuMachine, winMachine;
	private IGuestSession clientGSession, servGSession;
	private ISession servSession; 
	
	public EDRController() throws ControllerLoadFailure
	{
		boolean loaded = true;
		
		data = createControllerData();

		// Login to vbox.
		this.mgr = VBoxUtils.loginToVBox(Config.getVBoxUrl());
		this.vbox = mgr.getVBox();
		if (vbox == null) {
			logger.severe("EDR: EDRController: vbox is null");
			throw new ControllerLoadFailure("vbox is null");
		}

		ubuntuMachine = findMachine(SERVER_VM_NAME);
		winMachine = findMachine(CLIENT_VM_NAME);
	}

	// Here we decide what mime types are supported by this controller, among
	// other things
	private ControllerData createControllerData()
	{
		// Currently support all 'application' mime types, except pcaps.
		Predicate<MediaType> isSupported = (type) -> 
			(type.getType().equals("application") && 
			!type.equals(Utils.PCAP_MIME));
		return new ControllerData(CheckType.EDR, isSupported);
	}
	
	// Runs the given executable on the client VM.
	public void submitFileToVM(SampleFile sample) throws FileSubmittionFailedException
	{
		logger.info("EDR: submitting " + sample.getPath() + " for analysis");

		Path exe = sample.getPath();
		Path exeAsIso = null;
		ISession cSession = null;
		try {
			if (ubuntuMachine.getState() != MachineState.Running) {
				servSession = startServer(ubuntuMachine);
			}

			// session is null, only if the server machine is allready running
			// before the first file. This should be avoided. still.
			if (servSession == null)
				servSession = mgr.getSessionObject();
			logger.info("EDR: Server Machine is up...");

			// We need to set the EDR server to use the file's hash in ES index
			// name. run a script to set config.dat file
			runScriptOnServerVM(servSession, ubuntuMachine, sample);

			// Run the executable on windows.
			cSession = mgr.getSessionObject();
			startVM(cSession, winMachine, CLIENT_VM_SNAPSHOT);

			// In order to secure the host, we wrap the exe as iso, and
			// attach it to the guest as a DVD.
			exeAsIso = IsoCreator.createIso(exe);

			// Detach before, in case there is something attached...
			detachDVD(cSession, winMachine);
			attachIsoAsDVD(cSession, winMachine, exeAsIso);

			// some gap is needed before running edr else it doesn't get the
			// system
			// date correctly. also, the DVD needs some time to attach
			sleep(40);

			// Start the EDR
			logger.fine("EDR: Running edr");
			runProcessOnClientVM(cSession, winMachine, EDR_PROCESS.toString());
			sleep(10);

			// Launch the process file
			logger.info("EDR: Trying to run executable on guest");
			Path exeInGuest = GUEST_DVD.resolve(exe.getFileName());

			runProcessOnClientVM(cSession, winMachine, exeInGuest.toString(), 
					sample.getFileDetails().getParams());

			// Wait until the ES index is created.
			ESHandler es = ESHandler.getInstance();

			boolean idxCreated = es.notifyWhenEdrIndexIsCreated(sample, 8); // 8
																			// minutes
																			// timeout
			if (!idxCreated) {
				throw new FileSubmittionFailedException(exe, FailReason.ES_IDX_NOT_CREATED,
						"Waiting for es index timedout");
			}

			// Reindex the edr data received from server.
			// this is done in a separate thread.
			if (idxCreated) {
				es.reindexEdrAndDelete(sample);
			}

			// Catches all vbox exceptions from dvd attach, vm start, etc.
		} catch (VBoxException e) {
			logger.severe("EDR: VBOX exception: " + e.getMessage());
			logger.log(Level.FINER, "", e);

			throw new FileSubmittionFailedException(exe, FailReason.VBOX_ERROR, e.getMessage(), e);
		} finally {
			shutdownClientAndCleanup(cSession, exeAsIso);
		}

		// always good to consume event queue
		mgr.waitForEvents(0);
	}

	private void shutdownClientAndCleanup(ISession cSession, Path exeAsIso)
	{
		// Power down the machine
		logger.info("EDR: Shutting down windows guest");
		logger.info("-------------------------------------------");

		// Don't forget to run vboxwebsrv with -t 600 option (prolong session
		// timeout), otherwise session is 'null' here
		if (cSession != null) {
			detachDVD(cSession, winMachine);
			powerDownMachine(winMachine, cSession);
		}

		try {
			// A safety measure, for some reason the server sessions accumulate,
			// 'til the maximum 32.
			if (clientGSession != null)
				clientGSession.close();
			if (servGSession != null)
				servGSession.close();
		} catch (VBoxException e) {
			// safely ignore.
		}
		// delete iso file
		try {
			if (exeAsIso != null)
				Files.deleteIfExists(exeAsIso);
		} catch (IOException e) {
			// we don't really care
			logger.log(Level.FINE, "Deleting " + exeAsIso + " failed", e);
		}
	}

	// Start the given machine with the specified snapshot
	private void startVM(ISession session, IMachine machine, String snapshotName)
	{
		try {
			String name = machine.getName();
			logger.info("EDR: Attempting to start VM '" + name + "'");

			logger.fine("EDR: Session state: " + session.getState());
			logger.fine("EDR: Machine state: " + machine.getState());

			// no need to start
			if (machine.getState() == MachineState.Running) {
				logger.info("EDR: Machine " + machine.getName() + " is already running");
				return;
			}

			// Trying to lock a locked session crashes.
			if (session.getState() != SessionState.Locked)
				machine.lockMachine(session, LockType.Shared);

			// Restore the requested snapshot.
			ISnapshot s = session.getMachine().findSnapshot(snapshotName);

			IProgress p = session.getMachine().restoreSnapshot(s);

			// wait for the operation up to 20 secs
			boolean ret = progressBarPatch(p, TimeUnit.SECONDS.toMillis(15));
			logger.fine("EDR: Snapshot restored? " + ret);

			// must unlock before launch. Launch call tries to lock the session
			// and if it is already locked it will return 'session busy'
			session.unlockMachine();

			// p = machine.launchVMProcess(session, "gui", "");
			p = machine.launchVMProcess(session, "headless", "");
			ret = progressBarPatch(p, TimeUnit.SECONDS.toMillis(50));
			session.unlockMachine();
			logger.info("EDR: Machine " + machine.getName() + " is " + (ret ? "up" : "down"));
		} catch (VBoxException e) {
			logger.severe("EDR: failed to start vm " + machine.getName());
			logger.log(Level.FINE, "", e);
			throw e; // rethrow to cancel submission
		}
	}

	// Starts the edr server, and the keep alive thread
	private ISession startServer(IMachine ubuntuMachine)
	{
		// get a new session
		servSession = mgr.getSessionObject();
		startVM(servSession, ubuntuMachine, SERVER_VM_SNAPSHOT);

		// start keep alive thread (which is daemon)
		new ServerSessionKeepAlive(ubuntuMachine, servSession).startKeepAliveThread();

		return servSession;
	}

	private void attachIsoAsDVD(ISession cSession, IMachine winMachine, Path exeAsIso)
	{
		// only if controller support hotplug, else use Write
		winMachine.lockMachine(cSession, LockType.Shared);
		try {
			// Opening (must use absolute path ):).
			logger.fine("EDR: Opening medium " + exeAsIso.toString());
			IMedium medium = vbox.openMedium(exeAsIso.toAbsolutePath().toString(), DeviceType.DVD, AccessMode.ReadOnly,
					false);

			logger.fine("EDR: Trying to attach to guest");
			cSession.getMachine().attachDevice("SATA", 0, 0, DeviceType.DVD, medium);
			cSession.getMachine().saveSettings();
			cSession.unlockMachine();
		} catch (VBoxException e) {
			logger.severe("EDR: Cannot attach DVD. See log");
			throw e; // rethrow to cancel submission
		}
	}

	// Detach a medium from the VM - not needed (I think) because we
	// are shutting down the machine
	private void detachDVD(ISession cSession, IMachine winMachine)
	{
		try {
			winMachine.lockMachine(cSession, LockType.Shared);

			logger.fine("EDR: Detaching medium");
			cSession.getMachine().detachDevice("SATA", 0, 0);
			cSession.getMachine().saveSettings();
		} catch (VBoxException e) {
			logger.fine("EDR: Problem detaching DVD. Not a real concern. See log");
			// logger.log(Level.FINE, "", e);
		} finally {
			cSession.unlockMachine();
		}
	}

	private void runProcessOnClientVM(ISession cSession, IMachine winMachine, String processName)
	{
		runProcessOnClientVM(cSession, winMachine, processName, null);
	}

	private void runProcessOnClientVM(ISession cSession, IMachine winMachine, String processName, List<String> params)
	{
		winMachine.lockMachine(cSession, LockType.Shared);

		// Create a guest session
		IGuest guest = cSession.getConsole().getGuest();
		clientGSession = guest.createSession(CLIENT_VM_USER_NAME, CLIENT_VM_PASSWORD, "", "");
		logger.fine("EDR: Guest session created ");

		long startFlag = GuestSessionWaitForFlag.Start.value();
		clientGSession.waitFor(startFlag, TimeUnit.SECONDS.toMillis(15));

		logger.fine("EDR: Creating process " + processName);
		IGuestProcess process = null;

		// NOTE: In order for windows to allow 'setup' processes, uac was
		// cancelled
		process = clientGSession.processCreate(processName, params, null,
				Arrays.asList(ProcessCreateFlag.WaitForProcessStartOnly), TimeUnit.MINUTES.toMillis(10));
		logger.fine("EDR: Process status: " + process.getStatus());

		// We can't close the guest session here - because it will kill the
		// process. We do it on shutdown
		cSession.unlockMachine();
	}

	private void runScriptOnServerVM(ISession session, IMachine machine, SampleFile exe)
	{
		// We need to set the EDR server to use the file's hash in ES index
		// name. run a script to set config.dat file
		if (session.getState() != SessionState.Locked)
			machine.lockMachine(session, LockType.Shared);

		// Create a guest session
		IGuest guest = session.getConsole().getGuest();
		servGSession = guest.createSession(SERVER_VM_USER_NAME, SERVER_VM_PASSWORD, "", "");

		long startFlag = GuestSessionWaitForFlag.Start.value();
		servGSession.waitFor(startFlag, TimeUnit.SECONDS.toMillis(5));
		logger.fine("EDR: running script on server vm: ");

		// Get the file's hash, ES Only accept lower-cased indexes.
		// also - we use md5 here, because sha256 is too long for ES index name
		String md5Hash = exe.getMd5Hash();

		List<ProcessCreateFlag> pFlag = new ArrayList<ProcessCreateFlag>();
		pFlag.add(ProcessCreateFlag.WaitForProcessStartOnly);

		// The process params. The first one is ignored for some reason
		List<String> pArg = Arrays.asList("param1", md5Hash);
		IGuestProcess process = servGSession.processCreate("/home/efsuser/change_config.sh", pArg, null, pFlag,
				(long) 0);

		process.waitFor((long) ProcessWaitForFlag.Terminate.value(), 0L);
		logger.fine("EDR: Process status: " + process.getStatus());

		// run the edr server
		logger.fine("EDR: restarting edr server:");
		pFlag = new ArrayList<ProcessCreateFlag>();
		pFlag.add(ProcessCreateFlag.WaitForProcessStartOnly);

		process = servGSession.processCreate("/home/efsuser/restart_efs.sh", null, null, pFlag, (long) 10);

		process.waitFor((long) ProcessWaitForFlag.Terminate.value(), 5L);
		logger.fine("EDR: Process status: " + process.getStatus());
		logger.fine("EDR: Exit code: " + process.getExitCode());

		// Guest session will be closed later, to avoid killing the associated
		// process. (restarting efs)
		session.unlockMachine();
	}

	// Waits for a VBOX operation to finish, up to waitMillis milliseconds.
	
	// Note: For some unknow reason, vbox progress returns IMEDDIATELY on
	// ubuntu... Patch: sleep the given time instead.
	private boolean progressBar(IProgress p, long waitMillis)
	{
		long end = System.currentTimeMillis() + waitMillis;
		while (!p.getCompleted()) {
			// process system event queue
			mgr.waitForEvents(0);
			// wait for completion of the task, but at most 200 msecs
			// p.waitForCompletion(200);
			p.waitForCompletion(-1);
			
			if (System.currentTimeMillis() >= end)
				return false;
		}
		
		return true;
	}

	// Note: For some unknow reason, vbox progress returns IMEDDIATELY on
	// ubuntu... Patch: sleep the given time instead.
	private boolean progressBarPatch(IProgress p, long waitMillis)
	{
		try {
			TimeUnit.MILLISECONDS.sleep(waitMillis);
		} catch (InterruptedException e) {
			logger.finer("sleep interrupt: " + e.getMessage());
		}
		return true;
	}

	private void powerDownMachine(IMachine machine, ISession session)
	{
		try {
			machine.lockMachine(session, LockType.Shared);
			IConsole console = session.getConsole();
			if (console != null) {
				IProgress p = console.powerDown();
				progressBarPatch(p, TimeUnit.SECONDS.toMillis(20));
			}
			if (session.getState() == SessionState.Locked) {
				session.unlockMachine();
			}
		} catch (VBoxException e) {
			// we sometimes get rc=0x8000ffff The session is not locked
			// (session state: Unlocked), We can safely ignore this
			logger.finer("EDR: on power down: " + e.getMessage());
		}
	}

	private IMachine findMachine(String machineName) throws ControllerLoadFailure
	{
		IMachine machine = vbox.findMachine(machineName);
		if (machine == null) {
			throw new ControllerLoadFailure(
					"EDR: Couldn't find " + machineName + " vm!. exiting");
			
		}
		return machine;
	}

	private void sleep(int secs)
	{
		try {
			TimeUnit.SECONDS.sleep(secs);
		} catch (InterruptedException e) {
			logger.finer("EDR: sleep interrupt: " + e.getMessage());
		}
	}

	@Override
	public boolean getDataOnFile(SampleFile sample) throws FileSubmittionFailedException
	{
		submitFileToVM(sample);
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
		logger.info("Shutting down edr controller");
		
		// shutdown machines. keepalive thread is daemon.
		// Power down the machine
		logger.info("Shutting down vms");
		try{
			if (servSession != null && ubuntuMachine.getState() != MachineState.PoweredOff) {
				powerDownMachine(ubuntuMachine, servSession);
			}
		}catch(VBoxException e)
		{
			logger.fine("EDR: unable to shutdown server vm, please do it manualy");
		}
	}

	public static void main(String[] args) throws Exception
	{
		// config logger
		//Logger logger = ErrorLogger.getInstance().getLogger();
		Utils.setLoggerLevels(true);

		EDRController c = new EDRController();

		String benign_dir = "C:\\Users\\Assaf\\Desktop\\Verint\\TestFiles\\Benign\\";
		Path dir = Paths.get(benign_dir + "\\GetRight");

		// Load files
		List<Path> filesInFolder = Files.walk(dir).filter(Files::isRegularFile)
				// .map(Path::toFile)
				.collect(Collectors.toList());
		ErrorLogger.getInstance().getLogger().fine("Loaded " + filesInFolder.size() + " files");

		filesInFolder.forEach(f -> {
			try {

				c.submitFileToVM(new SampleFile(f));
			} catch (FileSubmittionFailedException e) {
				System.out.println("SUBMIT FAILED: " + e.getMessage());
				e.printStackTrace();
				System.out.println("-----------------------------------------------------");
			}
		});
	}

}
