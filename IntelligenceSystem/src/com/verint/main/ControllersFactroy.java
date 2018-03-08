package com.verint.main;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import com.verint.cnc.main.CncMainController;
import com.verint.edr.controller.EDRController;
import com.verint.exceptions.ControllerLoadFailure;
import com.verint.payload.PayloadController;
import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;

/**
 * A factory that loads controllers, allowing decoupling of 
 * system from controllers
 * 
 * @author Assaf Azaria
 *
 */
public class ControllersFactroy
{
	private Logger logger = ErrorLogger.getInstance().getLogger();
	
	// holder pattern, for lazy init and thread safety
	private static class SingletonHolder {
		private static ControllersFactroy instance = new ControllersFactroy(); 
	}
	
	private ControllersFactroy(){
	}
	
	public static ControllersFactroy getInstance()
	{
		return SingletonHolder.instance;
	}
	
	// Load the list of controllers according to policy
	public List<EngineController> getControllers()
	{
		List<EngineController> controllers = new ArrayList<>();
		
		// edr is first, because it is the longest.
		if (Config.runEdrController())
		{
			logger.info("Loading edr controller");
			try{
				EDRController c = new EDRController();
				controllers.add(c);
			}catch(ControllerLoadFailure e){
				logger.info(e.getMessage());
			}
			
		}
		
		if (Config.runCncController())
		{
			logger.info("Loading cnc controller");
			try{
				CncMainController c = new CncMainController();
				controllers.add(c);
			}catch(ControllerLoadFailure e){
				logger.info(e.getMessage());
			}
		}
		
		if (Config.runPayloadController())
		{
			logger.info("Loading payload controller");
			controllers.add(new PayloadController());
		}
		
		if (controllers.size() == 0)
		{
			logger.severe("No controllers were loaded, check config file");
		}
		
		return controllers;
	}
}
