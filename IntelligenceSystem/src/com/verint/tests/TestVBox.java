package com.verint.tests;
/* $Id: TestVBox.java 101270 2015-06-25 11:31:10Z klaus $ */

import java.util.Arrays;
import java.util.List;

/* Small sample/testcase which demonstrates that the same source code can
 * be used to connect to the webservice and (XP)COM APIs. */

/*
 * Copyright (C) 2010-2015 Oracle Corporation
 *
 * This file is part of VirtualBox Open Source Edition (OSE), as
 * available from http://www.virtualbox.org. This file is free software;
 * you can redistribute it and/or modify it under the terms of the GNU
 * General Public License (GPL) as published by the Free Software
 * Foundation, in version 2 as it comes in the "COPYING" file of the
 * VirtualBox OSE distribution. VirtualBox OSE is distributed in the
 * hope that it will be useful, but WITHOUT ANY WARRANTY of any kind.
 */
//import org.virtualbox_5_0.*;
import org.virtualbox_5_1.CPUPropertyType;
import org.virtualbox_5_1.GuestSessionWaitResult;
import org.virtualbox_5_1.HWVirtExPropertyType;
import org.virtualbox_5_1.IConsole;
import org.virtualbox_5_1.IEvent;
import org.virtualbox_5_1.IEventListener;
import org.virtualbox_5_1.IEventSource;
import org.virtualbox_5_1.IGuest;
import org.virtualbox_5_1.IGuestOSType;
import org.virtualbox_5_1.IGuestProcess;
import org.virtualbox_5_1.IGuestSession;
import org.virtualbox_5_1.IMachine;
import org.virtualbox_5_1.IMachineStateChangedEvent;
import org.virtualbox_5_1.IProgress;
import org.virtualbox_5_1.ISession;
import org.virtualbox_5_1.ISnapshot;
import org.virtualbox_5_1.IVirtualBox;
import org.virtualbox_5_1.IVirtualBoxErrorInfo;
import org.virtualbox_5_1.LockType;
import org.virtualbox_5_1.MachineState;
import org.virtualbox_5_1.SessionState;
import org.virtualbox_5_1.VBoxEventType;
import org.virtualbox_5_1.VBoxException;
import org.virtualbox_5_1.VirtualBoxManager;

// For now: vboxmanage setproperty websrvauthlibrary null
// disables auth login
public class TestVBox {
	
	static void processEvent(IEvent ev) {
		System.out.println("got event: " + ev);
		VBoxEventType type = ev.getType();
		System.out.println("type = " + type);
		switch (type) {
		case OnMachineStateChanged: {
			IMachineStateChangedEvent mcse = IMachineStateChangedEvent.queryInterface(ev);
			if (mcse == null)
				System.out.println("Cannot query an interface");
			else
				System.out.println("mid=" + mcse.getMachineId());
			break;
		}
		default:
			System.out.println("other");
		}
	}

	static class EventHandler {
		EventHandler() {
		}

		public void handleEvent(IEvent ev) {
			try {
				processEvent(ev);
			} catch (Throwable t) {
				t.printStackTrace();
			}
		}
	}

	static void testEvents(VirtualBoxManager mgr, IEventSource es) {
		// active mode for Java doesn't fully work yet, and using passive
		// is more portable (the only mode for MSCOM and WS) and thus generally
		// recommended
		IEventListener listener = es.createListener();

		es.registerListener(listener, Arrays.asList(VBoxEventType.Any), false);

		try {
			for (int i = 0; i < 50; i++) {
				System.out.print(".");
				IEvent ev = es.getEvent(listener, 500);
				if (ev != null) {
					processEvent(ev);
					es.eventProcessed(listener, ev);
				}
				// process system event queue
				mgr.waitForEvents(0);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		es.unregisterListener(listener);
	}

	static void testEnumeration(VirtualBoxManager mgr, IVirtualBox vbox) {
		List<IMachine> machs = vbox.getMachines();
		for (IMachine m : machs) {
			String name;
			Long ram = 0L;
			boolean hwvirtEnabled = false, hwvirtNestedPaging = false;
			boolean paeEnabled = false;
			boolean inaccessible = false;
			try {
				name = m.getName();
				ram = m.getMemorySize();
				hwvirtEnabled = m.getHWVirtExProperty(HWVirtExPropertyType.Enabled);
				hwvirtNestedPaging = m.getHWVirtExProperty(HWVirtExPropertyType.NestedPaging);
				paeEnabled = m.getCPUProperty(CPUPropertyType.PAE);
				String osType = m.getOSTypeId();
				IGuestOSType foo = vbox.getGuestOSType(osType);
			} catch (VBoxException e) {
				name = "<inaccessible>";
				inaccessible = true;
			}
			System.out.println("VM name: " + name);
			if (!inaccessible) {
				System.out.println(" RAM size: " + ram + "MB" + ", HWVirt: " + hwvirtEnabled + ", Nested Paging: "
						+ hwvirtNestedPaging + ", PAE: " + paeEnabled);
			}
		}
		// process system event queue
		mgr.waitForEvents(0);
	}

	static boolean progressBar(VirtualBoxManager mgr, IProgress p, long waitMillis) {
		long end = System.currentTimeMillis() + waitMillis;
		while (!p.getCompleted()) {
			// process system event queue
			mgr.waitForEvents(0);
			// wait for completion of the task, but at most 200 msecs
			p.waitForCompletion(200);
			if (System.currentTimeMillis() >= end)
				return false;
		}
		return true;
	}

	static void testStart(VirtualBoxManager mgr, IVirtualBox vbox) {
		IMachine m = vbox.getMachines().get(2);
		String name = m.getName();
		System.out.println("\nAttempting to start VM '" + name + "'");

		ISession session = mgr.getSessionObject();
		IProgress p = m.launchVMProcess(session, "gui", "");
		progressBar(mgr, p, 10000);
		session.unlockMachine();
		// process system event queue
		mgr.waitForEvents(0);
	}

	static final String SHARED_FOLDER = "C:\\Users\\Assaf\\Desktop\\vb_shared";

	static void testStart2(VirtualBoxManager mgr, IVirtualBox vbox) {
		// put a test file in shared folder

		// start machine

		IMachine winMachine = vbox.findMachine("Windows 7 (64)");
		// for (IMachine m : vbox.getMachines())
		// {
		// if (m.getName().startsWith("Windows"))
		// {
		// winMachine = m;
		// break;
		// }
		// }
		if (winMachine == null) {
			System.out.println("Couldn't find windows guest vm");
			System.exit(0);
		}
		ISession session = null;
		try {
			String name = winMachine.getName();
			System.out.println("\nAttempting to start VM '" + name + "'");

			session = mgr.getSessionObject();
			System.out.println(session.getState());
			System.out.println(winMachine.getState());
			if (winMachine.getState() != MachineState.Running) {
				System.out.println("Launching machine");
				if (session.getState() != SessionState.Locked)
					winMachine.lockMachine(session, LockType.Write);

				ISnapshot s = session.getMachine().findSnapshot("EDR_102_2");
				System.out.println(s);

				IProgress p = session.getMachine().restoreSnapshot(s);
				boolean ret = progressBar(mgr, p, 30000);
				System.out.println(ret);
//
//				// must unlock before launch - otherwise 'session busy'
				session.unlockMachine();
//
				p = winMachine.launchVMProcess(session, "gui", "");
				ret = progressBar(mgr, p, 90_000);
				System.out.println(ret);
				session.unlockMachine();

			}

			winMachine.lockMachine(session, LockType.Shared);

			System.out.println("Machine is up - launch process");

			IConsole console = session.getConsole();

			IGuest guest = console.getGuest();

			IGuestSession guestSession = guest.createSession("User01", "123qwe!@#", "", "");

			System.out.println("Guest session created ");

			Long time = 100_000L;
			// try {
			// System.out.println("Waiting 10 seconds. why?");
			// Thread.sleep(10000);
			// } catch (InterruptedException e) {
			// 
			// e.printStackTrace();
			// }
			GuestSessionWaitResult result = guestSession.waitFor(time, time);
			System.out.println(result);
			
			System.out.println("Creating process");
			IGuestProcess process = null;
			// if(result == GuestSessionWaitResult.Start)
			
			// maybe copy the file to somewhere
			//IProgress p = guestSession.fileCopy("E:\\winver.png", "C:\\", null);
			//progressBar(mgr, p, 10000);
			
			/// GOOD
			//process = guestSession.processCreate("notepad.exe", null, null, null, 0L);
			//process = guestSession.processCreate("e:\\winver.png", null, null, null, 0L);
			//process = guestSession.processCreate("e:\\hfs.exe", null, null, null, 0L);
			//process = guestSession.processCreate("e:\\kitty.exe", null, null, null, 0L);
			//process = guestSession.processCreate("e:\\install.exe", null, null, null, 0L);
			
			
			// In order for windows to allow 'setup' processes, uac was cancelled
			
			// BAD
			//process = guestSession.processCreate("e:\\mfevtps.exe", null, null, null, 0L);
			// process = guestSession.processCreate("e:\\DB.Browser.for.SQLite-3.9.1-win64.exe", null, null, null, 0L);
			process = guestSession.processCreate("e:\\Setup.exe", null, null, null, 0L);
			//process = guestSession.processCreate("C:\\Users\\User01\\7z1506-x64.exe", null, null, null, 0L);
			// process = guestSession.processCreate("e:\\7z1506-x64.exe", null, null, null, 0L);
			System.out.println(process.getStatus());
			try{
				Thread.sleep(5000);
			}catch(InterruptedException e){}
			System.out.println(process.getStatus());
			
			
			//ProcessWaitResult waitResult = process.waitFor((long)ProcessWaitForFlag.Start.value(), 0L);

//			if (waitResult == ProcessWaitResult.Start)
//				System.out.println("---started");
//
			session.unlockMachine();

			//

		} catch (VBoxException e) {
			if (session.getState() == SessionState.Locked)
				session.unlockMachine();
			e.printStackTrace();
		}

		// // launch a process from shared folder
		//
		// // process system event queue
		// mgr.waitForEvents(0);
	}

	static void testMultiServer() {
		VirtualBoxManager mgr1 = VirtualBoxManager.createInstance(null);
		VirtualBoxManager mgr2 = VirtualBoxManager.createInstance(null);

		try {
			mgr1.connect("http://i7:18083", "", "");
			mgr2.connect("http://main:18083", "", "");

			IMachine m1 = mgr1.getVBox().getMachines().get(0);
			IMachine m2 = mgr2.getVBox().getMachines().get(0);
			//String name1 = m1.getName();
			//String name2 = m2.getName();
			ISession session1 = mgr1.getSessionObject();
			ISession session2 = mgr2.getSessionObject();
			IProgress p1 = m1.launchVMProcess(session1, "gui", "");
			IProgress p2 = m2.launchVMProcess(session2, "gui", "");
			progressBar(mgr1, p1, 10000);
			progressBar(mgr2, p2, 10000);
			session1.unlockMachine();
			session2.unlockMachine();
			// process system event queue
			mgr1.waitForEvents(0);
			mgr2.waitForEvents(0);
		} finally {
			mgr1.cleanup();
			mgr2.cleanup();
		}
	}

	static void testReadLog(VirtualBoxManager mgr, IVirtualBox vbox) {
		IMachine m = vbox.getMachines().get(0);
		long logNo = 0;
		long off = 0;
		long size = 16 * 1024;
		while (true) {
			byte[] buf = m.readLog(logNo, off, size);
			if (buf.length == 0)
				break;
			System.out.print(new String(buf));
			off += buf.length;
		}
		// process system event queue
		mgr.waitForEvents(0);
	}

	static void printErrorInfo(VBoxException e) {
		System.out.println("VBox error: " + e.getMessage());
		System.out.println("Error cause message: " + e.getCause());
		System.out.println("Overall result code: " + Integer.toHexString(e.getResultCode()));
		int i = 1;
		for (IVirtualBoxErrorInfo ei = e.getVirtualBoxErrorInfo(); ei != null; ei = ei.getNext(), i++) {
			System.out.println("Detail information #" + i);
			System.out.println("Error mesage: " + ei.getText());
			System.out.println("Result code:  " + Integer.toHexString(ei.getResultCode()));
			// optional, usually provides little additional information:
			System.out.println("Component:    " + ei.getComponent());
			System.out.println("Interface ID: " + ei.getInterfaceID());
		}
	}

	public static void main(String[] args) {
		VirtualBoxManager mgr = VirtualBoxManager.createInstance(null);

		boolean ws = false;
		String url = null;
		String user = null;
		String passwd = null;

		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-w"))
				ws = true;
			else if (args[i].equals("-url"))
				url = args[++i];
			else if (args[i].equals("-user"))
				user = args[++i];
			else if (args[i].equals("-passwd"))
				passwd = args[++i];
		}

		if (ws) {
			try {
				mgr.connect(url, user, passwd);
			} catch (VBoxException e) {
				e.printStackTrace();
				System.out.println("Cannot connect, start webserver first!");
			}
		}

		System.out.println("----- Connection to " + url + " succeeded");
		try {
			IVirtualBox vbox = mgr.getVBox();
			if (vbox != null) {
				System.out.println("VirtualBox version: " + vbox.getVersion() + "\n");
				testEnumeration(mgr, vbox);
				// testReadLog(mgr, vbox);
				// testStart(mgr, vbox);
				testStart2(mgr, vbox);
				// testEvents(mgr, vbox.getEventSource());

				System.out.println("done, press Enter...");
				System.in.read();
			}
		} catch (VBoxException e) {
			printErrorInfo(e);
			System.out.println("Java stack trace:");
			e.printStackTrace();
		} catch (RuntimeException e) {
			System.out.println("Runtime error: " + e.getMessage());
			e.printStackTrace();
		} catch (java.io.IOException e) {
			e.printStackTrace();
		}

		// process system event queue
		mgr.waitForEvents(0);
		if (ws) {
			try {
				mgr.disconnect();
			} catch (VBoxException e) {
				e.printStackTrace();
			}
		}

		mgr.cleanup();

	}

}
