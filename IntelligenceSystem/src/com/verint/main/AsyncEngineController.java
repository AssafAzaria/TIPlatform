package com.verint.main;

/**
 * Extended interface for controllers that work asynchronously
 * 
 * @author Assaf Azaria
 */
public interface AsyncEngineController extends EngineController
{
	public void addListener(EngineListener l);
	public void removeListener(EngineListener l);
}
