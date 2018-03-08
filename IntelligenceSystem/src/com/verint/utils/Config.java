package com.verint.utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.yaml.snakeyaml.Yaml;

import com.verint.cnc.main.CncMachine;

/**
 * Holds config data for the system
 * @author Assaf Azaria
 */
@SuppressWarnings("unchecked")
public class Config {
	private static final String CONFIG_FOLDER = "config";
	private static final String CONFIG_FILE = "intelligence.yml";
	
	//////////////////////////////////////////////////////////////////////////////////
	private static final String RUN_EDR_CONTROLLER = "run.edr.controller";
	private static final String RUN_CNC_CONTROLLER = "run.cnc.controller";
	private static final String RUN_PAYLOAD_CONTROLLER = "run.payload.controller";
	private static final String RUN_MOLOCH_CONTROLLER = "run.moloch.controller";
	private static final String FILES_PATH = "submit.files.path";
	private static final String SAVE_PATH = "save.files.path";
	private static final String REPORT_PATH = "daily.report.path";
	
	private static final String VT_DOWNLOAD_DIR = "vt.downloads.dir";
	private static final String VT_PARAMS_FILE = "vt.params.file";
	
	//////////////////////////////////////////////////////////////////////////////////
	private static final String ES_HOST = "elastic.host";
	private static final String ES_URL = "elastic.url";
	private static final String ES_INDEX_NAME = "elastic.index.name";
	private static final String ES_CLUSTER_NAME = "elastic.cluster.name";
	private static final String ES_NODE_NAME = "elastic.node.name";
	
	private static final String VBOX_URL = "vbox.url";
	
	private static final String DDP_HOST = "ddp.host.name";
	private static final String CNC_HOSTS = "cnc.host.names";
	
	private static final String CNC_ENV = "cnc.environment";
	
	private static final String DDP_USER_NAME = "ddp.user.name";
	private static final String DDP_PASSWORD = "ddp.password";
	private static final String DDP_FILES_PATH = "ddp.files.path";
	private static final String DAP_LOG_FILE = "dap.log.file";
    private static final String DDP_LOG_FILE = "ddp.log.file";
   
    private static final String DSR_DB_NAME = "dsr.db.name";
	private static final String LAP_TABLE_NAME = "lap.table.name";
	private static final String ALERTS_DB_NAME = "alerts.db.name";
	
	private static final String DB_URL = "db.url";
	private static final String DB_USER = "db.user.name";
	private static final String DB_PASSWORD = "db.password";
	
	// holds the config properties
	private static Map<String, Object> configMap = new HashMap<>();
	
	public static String getESHost()
	{
		return (String)configMap.getOrDefault(ES_HOST, "localhost");
	}
	
	public static String getESUrl()
	{
		return (String)configMap.getOrDefault(ES_URL, "http://localhost:9200");
	}
	
	public static String getESIndexName()
	{
		return (String)configMap.getOrDefault(ES_INDEX_NAME, "intelligence");
	}
	
	public static String getESClusterName()
	{
		return (String)configMap.getOrDefault(ES_CLUSTER_NAME, "intelligence");
	}
	
	public static String getESNodeName()
	{
		return (String)configMap.getOrDefault(ES_NODE_NAME, "intelligence1");
	}
	
	
	public static String getVBoxUrl()
	{
		return (String)configMap.getOrDefault(VBOX_URL, "http://localhost:18083/");
	}
	
	public static String getProperty(String key)
	{
		return (String)configMap.getOrDefault(key, "empty");
	}
	
	
	public static String getDDPHost()
	{
		return (String)configMap.getOrDefault(DDP_HOST, "10.164.237.73");
	}
	
	
	public static List<CncMachine> getCNCHosts()
	{
		List<Map<String, Object>> hosts = 
				(List<Map<String, Object>>)configMap.get(CNC_HOSTS);
		
		if (hosts == null){
			return defaultCncMachine();
		}
		
		return hosts.stream()
				.map((host)-> new CncMachine((String)host.get("name"), 
											 (int)host.get("environment")))
				.collect(Collectors.toList());
	}
	
	private static List<CncMachine> defaultCncMachine(){
		return Collections.singletonList(new CncMachine("10.164.237.74", 200));
	}
	
	public static int getCNCEnvironment()
	{
		return (int)configMap.getOrDefault(CNC_ENV, 200);
	}
	
	public static String getDDPUserName()
	{
		return (String)configMap.getOrDefault(DDP_USER_NAME, "ubuntu");
	}
	
	public static String getDDPPassword()
	{
		return (String)configMap.getOrDefault(DDP_PASSWORD, "ubuntu");
	}
	
	public static String getDDPFilesPath()
	{
		return (String)configMap.getOrDefault(DDP_FILES_PATH, "/tmp/pcaps");
	}
	
	public static String getDapLogFile()
	{
		return (String)configMap.getOrDefault(DAP_LOG_FILE, "/opt/clearsky/dap/clearsky.0");
	}
	
	public static String getDDPLogFile()
	{
		return (String)configMap.getOrDefault(DDP_LOG_FILE, "/opt/clearsky/ddp/tcp_sender/clearsky.0");
	}
	
	public static String getDsrDBName()
	{
		return (String)configMap.getOrDefault(DSR_DB_NAME, "clearsky_buffer");
	}
	
	public static String getLapTableName()
	{
		return (String)configMap.getOrDefault(LAP_TABLE_NAME, "s_lap_monitor");
	}
	
	public static String getAlertsDBName()
	{
		return (String)configMap.getOrDefault(ALERTS_DB_NAME, "clearsky_data");
	}
	
	public static String getDBUrl()
	{
		return (String)configMap.getOrDefault(DB_URL, "localhost:3306");
	}
	
	public static String getDBUserName()
	{
		return (String)configMap.getOrDefault(DB_USER, "dapuser");
	}
	
	public static String getDBPassword()
	{
		return (String)configMap.getOrDefault(DB_PASSWORD, "cyber");
	}
	
	public static boolean runEdrController()
	{
		return configMap.getOrDefault(RUN_EDR_CONTROLLER, "false").equals(true);
	}
	
	public static boolean runPayloadController()
	{
		return configMap.getOrDefault(RUN_PAYLOAD_CONTROLLER, "false").equals(true);
	}
	
	public static boolean runMolochController()
	{
		return configMap.getOrDefault(RUN_MOLOCH_CONTROLLER, "false").equals(true);
	}
	
	public static boolean runCncController()
	{
		return configMap.getOrDefault(RUN_CNC_CONTROLLER, "false").equals(true);
	}
	
	public static String getFilesPath()
	{
		return (String)configMap.getOrDefault(FILES_PATH, "files_to_test");
	}
	
	public static String getSavePath()
	{
		return (String)configMap.getOrDefault(SAVE_PATH, "submitted_files");
	}
	
	public static String getDailyReportPath()
	{
		return (String)configMap.getOrDefault(REPORT_PATH, "daily_reports");
	}
	
	public static String getVtDownloadPath()
	{
		return (String)configMap.getOrDefault(VT_DOWNLOAD_DIR, "vt_downloads");
	}
	
	public static String getParamsPath()
	{
		return (String)configMap.getOrDefault(VT_PARAMS_FILE, "params.txt");
	}
	
	// loads the properties on first usage
	static {
		// make sure the folder and file are there
		try {
			Files.createDirectories(Paths.get(CONFIG_FOLDER));
		
			Yaml yaml = new Yaml();
			try (InputStream in = Files.newInputStream(Paths.get(CONFIG_FOLDER + "/" + CONFIG_FILE))) {
				configMap = (Map<String, Object>)yaml.load(in);
			
				//System.out.println(configMap);
				ErrorLogger.getInstance().getLogger().fine("config file loaded");
			}
		}
		catch(IOException e)
		{
			ErrorLogger.getInstance().getLogger().warning("Config file not found");
		}
		
	}
	
	public static void main(String[] args) {
		List<CncMachine> o = Config.getCNCHosts();
		System.out.println(o);
		System.out.println("--------------------");
		
		for (CncMachine item : o)
		{
			System.out.println(item);
		}
		
		
		
	}
	
}


