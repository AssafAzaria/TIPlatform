package com.verint.tests;

import static org.elasticsearch.common.xcontent.XContentFactory.jsonBuilder;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class TestBasic {

//	void test()
//	{
//		final CountDownLatch done = new CountDownLatch(1);
//
//		new Thread(new Runnable() {
//
//		    @Override
//		    public void run() {
//		        //longProcessing();
//		        done.countDown();
//		    }
//		}).start();
//
//		//in your waiting thread:
//		boolean processingCompleteWithin1Second = done.await(1, TimeUnit.SECONDS);
//	}
	
	
	
	static TransportClient client = null;
	public static void main(String[] args) throws Exception {
		// on startup

		// If you want to set the cluster name etc.
		Settings settings = Settings.builder().put("cluster.name", "elasticsearch").build();
		// .put("client.transport.sniff", true)

		client = new PreBuiltTransportClient(settings);
		client.addTransportAddress(new InetSocketTransportAddress(
				InetAddress.getByName("localhost"), 9300));

		
//		indexDoc();
//		search();
//		// GetResponse response = client.prepareGet("twitter", "tweet",
//		// "1").get();
//		GetResponse response = client
//				.prepareGet("8de9568838aa2071d2f46c2ad6974c98-events-170425", "session", "AVulon4LoRV8U0CjxzHO").get();
//
//		GetResponse response2 = client
//				.prepareGet("8de9568838aa2071d2f46c2ad6974c98-events-170425", "session", "AVulooA8oRV8U0CjxzHR").get();
//
//		GetResponse response3 = client
//				.prepareGet("8de9568838aa2071d2f46c2ad6974c98-events-170425", "session", "AVuloeKroRV8U0CjxzDD").get();
//
//		System.out.println(toPrettyFormat(response.getSourceAsString()));
//		System.out.println(toPrettyFormat(response2.getSourceAsString()));
//		System.out.println(toPrettyFormat(response3.getSourceAsString()));
//		searchAll();
		
		// System.out.println(checkIfIndexExists("8de9568838aa2071d2f46c2ad6974c98-events-170425"));
		
		
//		notifyWhenIndexIsCreated("testing123", 2, new IndexCreatedListener() {
//			public void timeout() {
//				System.out.println("Timeout");
//			}
//			
//			@Override
//			public void indexCreated(String indexName) {
//				System.out.println("index created " + indexName);
//			}
//		});
	}
	
	static void searchAll() {
		
		// MatchAll on the whole cluster with all default options
		SearchResponse response = client.prepareSearch().get();
		System.out.println(response.getHits().getTotalHits());
		for (SearchHit hit : response.getHits())
		{
			System.out.println(hit.getIndex() + " : " + hit.getId());
			System.out.println("********************************");
			System.out.println(toPrettyFormat(hit.getSourceAsString()));
		}
	}
	static void search() {
		SearchResponse response = client.prepareSearch("8de9568838aa2071d2f46c2ad6974c98-events-170425")
				.setTypes("session")
				.setSearchType(SearchType.DFS_QUERY_THEN_FETCH)
				.setQuery(QueryBuilders.termQuery("host", "win7")) // Query
				//.setPostFilter(QueryBuil ders.rangeQuery("age").from(12).to(18)) // Filter
				.setFrom(0).setSize(60).setExplain(true).get();
		for (SearchHit hit : response.getHits())
		{
			System.out.println(hit.getId());
			System.out.println("********************************");
			System.out.println(toPrettyFormat(hit.getSourceAsString()));
		}
		//System.out.println(response.getHits().getAt(1).getSourceAsString());
		//System.out.println(toPrettyFormat(response.getSourceAsString()));

	}
  
	public static String toPrettyFormat(String jsonString) {
		JsonParser parser = new JsonParser();
		JsonObject json = parser.parse(jsonString).getAsJsonObject();

		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String prettyJson = gson.toJson(json);

		return prettyJson;
	}

	static void indexDoc() throws IOException {
		String json1 = "{" + "\"user\":\"kimchy\"," + "\"postDate\":\"2013-01-30\","
				+ "\"message\":\"trying out Elasticsearch\"" + "}";

		Map<String, Object> json2 = new HashMap<String, Object>();
		json2.put("user", "kimchy");
		json2.put("postDate", new Date());
		json2.put("message", "trying out Elasticsearch");

		XContentBuilder builder = jsonBuilder().startObject().field("user", "kimchy").field("postDate", new Date())
				.field("message", "trying out Elasticsearch").endObject();
		String json3 = builder.string();
		// on shutdown
		// client.close();

		IndexResponse response = client.prepareIndex("twitter", "tweet", "1").setSource(json2).get();

		// Index name
		String _index = response.getIndex();
		// Type name
		String _type = response.getType();
		// Document ID (generated or not)
		String _id = response.getId();
		// Version (if it's the first time you index this document, you will
		// get: 1)
		long _version = response.getVersion();
		// status has stored current instance statement.
		RestStatus status = response.status();

		System.out.println("index: " + _index);
		System.out.println("type: " + _type);
		System.out.println("id: " + _id);
		System.out.println("version: " + _version);
		System.out.println("status: " + status);

	}

}
