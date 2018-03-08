package com.verint.main;

/**
 * Represents a test to perform on a sample
 * 
 * @author Assaf Azaria
 */
public class PerformedTest
{
	private final CheckType checkType;
	private final boolean success;
	private final boolean supported;
	private final String description;
	
	private PerformedTest(Builder builder)
	{
		this.checkType = builder.checkType;
		this.success = builder.success;
		this.description = builder.description;
		this.supported = builder.supported;
	}
	
		
	public static class Builder{
		private final CheckType checkType;
		private boolean success = false;
		private boolean supported = true;
		private String description = "";
		
		public Builder(CheckType checkType){
			this.checkType = checkType;
		}
		
		public Builder success(boolean isSuccess){
			this.success = isSuccess;
			return this;
		}
		
		public Builder supported(boolean isSupported){
			this.supported = isSupported;
			return this;
		}
		
		public Builder description(String desc){
			this.description = desc;
			return this;
		}
		
		public PerformedTest build(){
			return new PerformedTest(this);
		}
	
	
	}
	
	public CheckType getCheckType()
	{
		return checkType;
	}
	
	public boolean isSupported()
	{
		return supported;
	}
	
	public boolean isSuccess()
	{
		return success;
	}
	
	@Override
	public String toString()
	{
		return String.format("TestStatus [CheckType=%s, supported=%s, success=%s, description=%s]", 
				checkType, supported, success, description);
	}
}
