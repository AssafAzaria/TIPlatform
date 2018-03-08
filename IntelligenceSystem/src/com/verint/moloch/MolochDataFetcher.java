package com.verint.moloch;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.mashape.unirest.request.HttpRequest;
import com.verint.utils.ErrorLogger;

// TODO: Fine tune the urls using moloch api.
public class MolochDataFetcher {
	private static final String HOST = "10.168.233.5";
	// private static final String HOST = "10.0.0.1";
	
	private static final int PORT = 8005;
	private static final String MOLOCH_USER = "admin";
	private static final String MOLOCH_PASS = "admin";
	
	private Logger logger = ErrorLogger.getInstance().getLogger();
	
	private String host;
	private int port;
	private URI baseUrl;

	public MolochDataFetcher(String host, int port) {
		this.host = host;
		this.port = port;

		baseUrl = getBaseUrl();

		setUpUnirest();
	}

	public MolochDataFetcher() {
		this(HOST, PORT);
	}

	//
	// Api
	//
	public void getSessionsData(long startTime, long endTime) {
		logger.fine("Moloch: get session data");
		getMolochData(startTime, endTime, DataType.SESSIONS);
	}

	public void getConnectionsData(long startTime, long endTime) {
		logger.fine("Moloch: get connection data");
		getMolochData(startTime, endTime, DataType.CONNECTIONS);
	}
	
	public void getSpiGraphData(long startTime, long endTime) {
		logger.fine("Moloch: get spigraph data");
		getMolochData(startTime, endTime, DataType.SPI_GRAPH);
	}
	
	public void getSpiViewData(long startTime, long endTime) {
		logger.fine("Moloch: get spiview data");
		getMolochData(startTime, endTime, DataType.SPI_VIEW);
	}
	

	private void getMolochData(long startTime, long endTime, DataType type)
	{
		
		String url = new URIBuilder(baseUrl)
				.setPath("/" + type + ".json")
				.toString();
		try {
			HttpRequest req = Unirest.get(url)
					.queryString("startTime", startTime)
					.queryString("endTime", endTime)
					.queryString("bounding", "database"); // TODO: add to generic config?
					//.queryString("strictly", "true"); 
			logger.fine(req.getUrl());		
			HttpResponse<JsonNode> res 	= req.asJson();

			logger.fine("---------------------");

			logger.fine("Response: " + res.getStatus());
			logger.fine(res.getBody().toString());
		} catch (UnirestException e) {
			logger.log(Level.SEVERE, "Moloch connection problem", e);
		}

	}
	
	// Unirest base configuration
	private void setUpUnirest() {
		// Set headers common to every Connect API request
		// Unirest.setDefaultHeader("Authorization", "");
		Unirest.setDefaultHeader("Accept", "application/json");
		Unirest.setHttpClient(createCustomHttpClient());
	}

	// Create and configure the HttpClient Unirest uses,
	// to support digest auth.
	private HttpClient createCustomHttpClient() {
		CredentialsProvider credsProvider = new BasicCredentialsProvider();
		credsProvider.setCredentials(
				new AuthScope(host, port), 
				new UsernamePasswordCredentials(MOLOCH_USER, MOLOCH_PASS));

		return HttpClients.custom().setDefaultCredentialsProvider(credsProvider).build();
	}

	private URI getBaseUrl()
	{
		try{
		return new URIBuilder()
				.setScheme("http")
				.setHost(host)
				.setPort(port)
				.build();
		}catch(URISyntaxException e)
		{
			logger.log(Level.INFO, "Moloch Url problem", e);
		}
		return null;
	}

	
}
