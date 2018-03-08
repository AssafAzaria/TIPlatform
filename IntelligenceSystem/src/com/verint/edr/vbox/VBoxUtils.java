package com.verint.edr.vbox;

import java.util.logging.Level;
import java.util.logging.Logger;

//import org.virtualbox_5_0.*;
import org.virtualbox_5_1.IVirtualBox;
import org.virtualbox_5_1.VBoxException;
import org.virtualbox_5_1.VirtualBoxManager;

import com.verint.exceptions.ControllerLoadFailure;
import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;

// Handles connection to VBox web service
// Note: It is essential to run \bin\vboxwebsrv -t 500. 
// to define a longer session timeout than the default 5 minutes.
// Also, Web service requires a login session. For now: we disabled authorization
// using cmd: vboxmanage setproperty websrvauthlibrary null
public class VBoxUtils {
	private static Logger logger = ErrorLogger.getInstance().getLogger();
	
	// Login to VBox using web service. run \bin\vboxwebsrv -t 500 on server
	// first.
	public static VirtualBoxManager loginToVBox() {
		return loginToVBox(Config.getVBoxUrl());
	}
	
	public static VirtualBoxManager loginToVBox(String vboxUrl) {
		if (!vboxUrl.startsWith("http")) vboxUrl = "http://" + vboxUrl;
		
		VirtualBoxManager mgr = VirtualBoxManager.createInstance(null);

		// Web service requires a login session. For now: we disabled authorization
		// using cmd: vboxmanage setproperty websrvauthlibrary null
		// TODO: deal with login auth
		logger.fine("-- logging in to vbox web service in url: " + vboxUrl);
				
		String url = vboxUrl;
		String user = null;
		String passwd = null;

		try { 
			mgr.connect(url, user, passwd);
			IVirtualBox vbox = mgr.getVBox();
			if (vbox != null) {
				logger.fine("VirtualBox version: " + vbox.getVersion() + "\n");
			}

		} catch (VBoxException e) {
			logger.severe("Cannot login to vbox, start webserver first!");
			logger.log(Level.FINE, "", e);
			throw new ControllerLoadFailure(e);
		}

		logger.info("----- Connection to " + url + " succeeded");
		return mgr;
	}

	// disconnect and allow server to clean session resources
	private static void logout(VirtualBoxManager mgr) {
		mgr.waitForEvents(0);
		try {
			mgr.disconnect();
		} catch (VBoxException e) {
			logger.log(Level.FINE, "problem logging out", e);
		}
		mgr.cleanup();
	}
}
