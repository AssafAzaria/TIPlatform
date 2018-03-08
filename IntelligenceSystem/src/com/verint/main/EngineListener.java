package com.verint.main;

import com.verint.exceptions.FileSubmittionFailedException;

public interface EngineListener
{
	public void onResponse(Response res);
	public void onError(SampleFile sample, FileSubmittionFailedException e);
}
