package com.verint.edr.controller;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

//import org.virtualbox_5_0.*;
import org.virtualbox_5_1.IMachine;
import org.virtualbox_5_1.ISession;
import org.virtualbox_5_1.LockType;

import com.verint.utils.ErrorLogger;


/** 
 * We need the server machine reachable through a session, 
*  because we use a web service, we need to keep the session busy
*  NOTE: we build on 5-6 minutes interval, vbox web service must be run with at least -t 400
*/ 
//Thought - do we really need this? it appears that it keeps reference to a machine by name. 
//Check this. Maybe we can get access to it with a new session
public class ServerSessionKeepAlive 
{
	private static final long DELAY = 5;
	private static Logger logger = ErrorLogger.getInstance().getLogger();
	
	private IMachine serverMachine;
	private ISession session;
	private ScheduledExecutorService executor = 
			Executors.newSingleThreadScheduledExecutor(new DaemonThreadFactory());
	private ScheduledFuture<?> future;
	
	public ServerSessionKeepAlive(IMachine serverMachine, ISession session)
	{
		this.serverMachine = serverMachine;
		this.session = session;
	}
	
	
	private final Runnable keepAlive = () -> {
	    try {
	        serverMachine.lockMachine(session, LockType.Shared);
	        long size = serverMachine.getMemorySize();
	        logger.info("EDR: Server keep alive: size: " + size);
	        session.unlockMachine();
	        
	        // There is a flaw in ScheduledService, that a thrown exception stops
	    	// execution of further tasks and with NO log. Catch all to keep
	        // the keep alive going.
	    } catch (Exception e) {  
	        logger.severe("EDR: Keep Alive thread halted");
	        logger.log(Level.FINE, "", e);
	        
	    }
	};
	
	public void startKeepAliveThread()
	{
		if (future == null || future.isDone())
		{	
			System.out.println("EDR: Starting keep alive");
			future = executor.scheduleWithFixedDelay(keepAlive, 3L, DELAY, TimeUnit.MINUTES);
		}
	}
	
	public void stopKeepAlive()
	{
		logger.log(Level.FINE, "EDR: shutting down keep alive");
		if (future != null && !future.isDone())
			future.cancel(true);
		executor.shutdownNow();
	}
	
	// to make threads daemon on executor
	class DaemonThreadFactory implements ThreadFactory {
	     public Thread newThread(Runnable r) {
	         Thread thread = new Thread(r);
	         thread.setDaemon(true);
	         return thread;
	     }
	}
}

