package com.verint.es;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.StringJoiner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.DocWriteRequest.OpType;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexResponse;
import org.elasticsearch.action.bulk.byscroll.BulkByScrollResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.reindex.ReindexAction;
import org.elasticsearch.index.reindex.ReindexRequestBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.json.JSONObject;

import com.verint.cnc.cnc.CNCData;
import com.verint.cnc.cnc.DSRData;
import com.verint.main.SampleFile;
import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;

/**
 * handler for Elastic Search
 * 
 * 
 * @author Assaf Azaria
 */
// ES uses runtime exceptions without much documentation. We catch them on the 
// main system loop.
public class ESHandler {
	
	// Singleton holder, for lazy init and thread safety
	private static class Holder {
		private static ESHandler instance = new ESHandler();
	} 
	
	private TransportClient client;
	private Logger logger = ErrorLogger.getInstance().getLogger();
	
	private ExecutorService executor = Executors.newSingleThreadExecutor();
	
	
	private ESHandler()
	{
		initESClient();
	}
	
	private final void initESClient(){
		client = new PreBuiltTransportClient(Settings.builder().
				put("cluster.name", Config.getESClusterName()).
				put("node.name", Config.getESNodeName()).
				build());
		try {
			client.addTransportAddress(new InetSocketTransportAddress(
					InetAddress.getByName(Config.getESHost()), 9300));
		} catch (UnknownHostException e) {
			logger.severe("cannot connect to elastic search");
			logger.log(Level.FINE, "", e);
		}
	}

	public static ESHandler getInstance() {
		return Holder.instance;
	}
	
	//
	// Generic functions
	//
	
	// poll ES for the given index, until timeout.  
	private boolean notifyWhenIndexIsCreated(String indexName, int timeoutMinutes)
	{
		logger.fine("expected index name: " + indexName);
		Instant start = Instant.now();
	    while(!checkIfIndexExists(indexName))
	    {
	    	long timePassed = Duration.between(start, Instant.now()).toMinutes();
	    	
	    	if (timePassed >= timeoutMinutes){
	    		logger.info("ES index was not created. Reached timeout");
	    		return false;
	    	}
	    	
	    	try {
	    		// Repeat check every 15 secs
				TimeUnit.SECONDS.sleep(15);
			} catch (InterruptedException e) {
				logger.finer("sleep interrupt: " + e.getMessage());
			}
	    }
	    logger.info("ES index created: " + indexName);
		return true;
	}
	
	// Check if the given index exists in ES
	private boolean checkIfIndexExists(String index) {
		IndexMetaData indexMetaData = client.admin().cluster()
	            .state(Requests.clusterStateRequest())
	            .actionGet()
	            .getState()
	            .getMetaData()
	            .index(index);

	    return (indexMetaData != null);
	}
	
	// index the base sample document for the given file
	public void indexSampleDoc(SampleFile file)
	{
		logger.info("Indexing sample: " + file.getPath().getFileName());
		logger.info("Exists? : " + Files.exists(file.getPath()));
		// Create doc
		try {
			XContentBuilder builder = jsonBuilder()
				    .startObject()
				        .field("file_name", file.getPath().getFileName().toString())
				        .field("file_hash", file.getSha256Hash())
				        .field("md5_hash", file.getMd5Hash())
				        .field("source", file.getSource())
				        .field("maliciousness", file.getMaliciousness())
				        .field("timestamp", Instant.now())
				        .field("context_id", file.getContext().getContextId())
				        .field("search string", file.getSearchString())
				        .field("description", file.getContext().getDescription())
				        .field("running_step", file.getContext().getRunningStep())
				    .endObject();
		
			System.out.println("INDEX: " + Config.getESIndexName());
			// use file hash as the document id
			IndexResponse response = client.prepareIndex(Config.getESIndexName(), 
					"sample", file.getEsId())
			        .setSource(builder)
			        .get();
			
			logger.info("ESHandler: Sample indexed: type: " + response.getType() +
					"id = " + response.getId() + 
					"version= " + response.getVersion() +
					"status= " + response.status());
		} catch (IOException e) {
			logger.log(Level.SEVERE, "ESHandler: Problem creating json for sample");
		}catch(ElasticsearchException e){
			logger.log(Level.SEVERE, e.getMessage(), e);
		}
	}
	
	// Reindex into from given srcIndex into our index.
	protected void reindex(String srcIndex, Script script)
	{
		reindex(srcIndex, Config.getESIndexName(), script);
	}
	
	protected void reindex(String srcIndex, String destIndex, Script script)
	{
		logger.info("ESHandler: reindexing: " + srcIndex);
		
		// Create base reindex request from src to dest.
		ReindexRequestBuilder builder = ReindexAction.INSTANCE.newRequestBuilder(client)
				.source(srcIndex).destination(destIndex);
		
		// optype create - we expect only new documents and no updates
		builder.destination().setOpType(OpType.CREATE);
		builder.script(script);
		
		try{
			BulkByScrollResponse res =  builder.get();
		}catch(ElasticsearchException e){
			logger.log(Level.SEVERE, "ES Failed to reindex", e);
		}
	}
	
	protected void deleteIndex(String indexName)
	{
		logger.info("ESHandler: deleting: " + indexName);
		DeleteIndexResponse res = 
				 client.admin().indices()
				.delete(new DeleteIndexRequest(indexName))
				.actionGet();
		boolean success = res.isAcknowledged();
		logger.fine("ESHandler: Index deleted? " + success);
	}
	
	//
	// Cnc functions
	//
	public void indexCncDoc(SampleFile file, CNCData cncData) {
		logger.info("ESHandler: Indexing cnc data on " + cncData.getPcapName() +
				" from: " + cncData.getCncMachine().getHostName());
		logger.info("ESHandler: Parent sample: " + file.getPath() +
				" hash: " + file.getSha256Hash() + 
				" es id: " + file.getEsId());
		
		
		int env = cncData.getCncMachine().getEnvironment();
		for (DSRData dsr : cncData.getDsrs()){
	 		// Create doc
			try {
				XContentBuilder builder = jsonBuilder().startObject()
						.field("file_name", cncData.getPcapName())
						.field("score", dsr.getScore())
						.field("host_category_id", dsr.getHostCategoryId())
						.field("black_list", dsr.isInBlackList())
						.field("white_list", dsr.isInWhiteList())
						.field("environment", env)
						.field("dsr", dsr.getFeatures())
						
						.endObject();
				
				// ES client is TS (stateless)
				IndexResponse response = client.prepareIndex(Config.getESIndexName(), 
						"cnc_data", "" + dsr.getRowId()).setSource(builder)
						.setParent(file.getEsId()).get();
	
//				logger.info("indexed: type: " + response.getType() + 
//						" id = " + response.getId() + " version= " + 
//						response.getVersion() + " status= " + 
//						response.status());
			} catch (IOException e) {
				logger.log(Level.SEVERE, "ESHandler: Problem creating json for cnc data");
			}catch(ElasticsearchException e){
				logger.log(Level.SEVERE, "ES Failed to index cnc doc", e);
			}
		}
	}
	
	//
	// Payload functions
	//
	public void indexPayloadReport(SampleFile sample, JSONObject report)
	{
		logger.info("ESHandler: Indexing payload response on " + sample.getPath());
		
		// ES client is TS (stateless)
		try{
			IndexResponse response = client.prepareIndex(Config.getESIndexName(), 
					"sandbox_data").setSource(report.toString(), XContentType.JSON)
					.setParent(sample.getEsId()).get();
		}catch(ElasticsearchException e){
			logger.log(Level.SEVERE, "ES Failed to index payload report", e);
		}
	}
	
	//
	// Edr functions
	//
	
	
	// poll ES for edr index creation, until timeout. 
	public boolean notifyWhenEdrIndexIsCreated(SampleFile file, int timeoutMinutes)
	{
		// use md5 because ES index name size limit
		return notifyWhenIndexIsCreated(buildEdrIndexName(file.getMd5Hash()), 
				timeoutMinutes);
	}
	
	// Builds the index name used by the EDR server.
	// [prefix]-events-[yyMMdd]
	private String buildEdrIndexName(String prefix) {
		String now = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
		return new StringJoiner("-")
				.add(prefix)
				.add("events")
				.add(now).toString();
	}
	
	// After we get the index from edr server, we reindex it in our own
	public void reindexEdrAndDelete(SampleFile file)
	{
		String edrIndexName = buildEdrIndexName(file.getMd5Hash());
		
		executor.submit(() -> {
			// Wait for edr server bulk to finish
			try{
				TimeUnit.SECONDS.sleep(70);
			}catch(InterruptedException e)
			{
				logger.finer("ESHandler: sleep interrupt: " + e.getMessage());
			}
			
			reindex(edrIndexName, createReindexScript(file.getEsId()));
			
			deleteIndex(edrIndexName);
		});
	}
	
	// Creates a script to tune the reindex process
	private Script createReindexScript(String parent)
	{
		StringBuilder builder = new StringBuilder();
		builder.append("if (ctx._type == 'session') {");
		builder.append("ctx._type = 'edr_data';");
		builder.append("ctx._parent = '");
		builder.append(parent);
		builder.append("'}");
		return new Script(builder.toString());
	}
	
	
}
