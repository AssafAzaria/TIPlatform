package com.verint.es;

/**
 *  Get indication when an index was created
 *  
 *  @author Assaf Azaria
 */
public interface IndexCreatedListener {
	public void indexCreated(String indexName);
	public void timeout();
}
