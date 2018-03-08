package com.verint.cnc.cnc;


import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.json.JSONObject;

import com.verint.exceptions.DaoException;
import com.verint.utils.Config;
import com.verint.utils.ErrorLogger;

/**
 * Handles db access for CNC data
 * @author Assaf Azaria
 */
public class CNCDao {
	private Logger logger = ErrorLogger.getInstance().getLogger();
	
	public CNCDao() {
	}
	
	// Get all dsrs of the given pcap
	public List<DSRData> getDsrsInfo(String pcapName) throws DaoException
	{
		Connection con = connect(Config.getDsrDBName());
		try{
			String sql = "SELECT * FROM dsrtable WHERE mgm_inputName_firstString LIKE ?";
			PreparedStatement stat = con.prepareStatement(sql);
			stat.setString(1, "%" + pcapName);
			ResultSet rs = stat.executeQuery();
			
			List<JSONObject> dsrsJson = convertToJSON(rs);
			logger.info("Dao: got " + dsrsJson.size() + " dsrs for pcap " + pcapName);
			return buildDsrDataList(dsrsJson);
		}catch(SQLException  e)
		{
			// Translate and pass on - 
			throw new DaoException("db exception, see log", e);
		}
		finally{
			disconnect(con);
		}
	}
	
	// Get the last row that LAP read from dsr table. We use this to
	// know when we can excpect an alert
	public int getLapLastReadRow() throws DaoException
	{
		int lastRowId = 0;
		Connection con = connect(Config.getAlertsDBName());
		try{
			String sql = "select String_value from " + Config.getLapTableName() + 
						" where Field_name='LastDsrRead'";
			
			ResultSet rs = con.createStatement().executeQuery(sql);
			rs.next(); // only one line (RS is never null by definition)
			lastRowId = rs.getInt(1);
			
			logger.info("Dao: last row read by LAP: " + lastRowId);
			
		}catch(SQLException e){
			// Translate and pass on - 
			throw new DaoException("db exception, see log", e);
		}
		finally{
			disconnect(con);
		}
		
		return lastRowId;
	}
	 
	// Get the (behavioural) alert (including score) for the given dsr
	public Optional<Alert> getDsrScore(DSRData dsr) throws DaoException
	{
		Optional<Alert> retVal = Optional.empty();
		
		Connection con = connect(Config.getAlertsDBName());
		try{
			PreparedStatement stat = con.prepareStatement("select confidence, hide_mask, "
					+ "mgm_hostName_firstString, mgm_serverIP_firstString "
					+ "from e_alerts where alert_type=? AND dsr_row_id=?");
			stat.setInt(1, Alert.TYPE_BEHAVIORAL);
			stat.setInt(2, dsr.getRowId());
			
			ResultSet rs = stat.executeQuery();
			// if there is a result
			if (rs.next())
			{
				retVal = Optional.of(readAlertFromRS(rs));
				logger.finer("DAO: got alert. " + retVal.get());
			}
		}catch(SQLException e){
			// Translate and pass on - 
			throw new DaoException("db exception, see log", e);
		}
		finally{
			disconnect(con);
		}
		
		return retVal;
	}
	
	
	public boolean isInBlackList(String hostName, String serverIp)
	{
		Connection con = connect(Config.getAlertsDBName());
		try{
			PreparedStatement stat = con.prepareStatement("select mgm_hostName_firstString, "
					+ "mgm_serverIP_firstString from e_alerts WHERE alert_type=?");
			stat.setInt(1, Alert.TYPE_REPUTATIONAL);
			
			ResultSet rs = stat.executeQuery();
			// if there is a result
			while (rs.next())
			{
				String host = rs.getString("mgm_hostName_firstString");
				String ip = rs.getString("mgm_serverIP_firstString");
				
				logger.finer("Checking reputational alert, host: " + host + ", ip " + ip);
				return (host.equals(hostName) || ip.equals(serverIp));
			}
		}catch(SQLException e){
			// Translate and pass on - 
			throw new DaoException("db  exception, see log", e);
		}
		finally{
			disconnect(con);
		}
		
		return false;
	}
	
	//
	// Helper methods
	//
	// Build DSRData objects from the given parsed Json data
	private List<DSRData> buildDsrDataList(List<JSONObject> dsrsJson)
	{
		List<DSRData> result = new LinkedList<>();
		
		dsrsJson.stream().forEach(dsr -> {
			result.add(new DSRData(buildDsrFeaturesMap(dsr)));
		});
		
		return result;
	}
	
	// Converts a single dsr into a Map.
	private Map<String, String> buildDsrFeaturesMap(JSONObject dsrJson)
	{
		Map<String, String> features = new HashMap<>();
		for (String key : JSONObject.getNames(dsrJson))
		{
			features.put(key, dsrJson.get(key).toString());
		}
		return features;
	}
	
	// Converts sql db data into json.
	private List<JSONObject> convertToJSON(ResultSet resultSet) throws SQLException{
        List<JSONObject> result = new ArrayList<>();
        
        while (resultSet.next()) {
            int total_cols = resultSet.getMetaData().getColumnCount();
            JSONObject dsr = new JSONObject();
            for (int i = 0; i < total_cols; i++) {
            	dsr.put(resultSet.getMetaData().getColumnLabel(i + 1).toLowerCase(), 
                		resultSet.getObject(i + 1));
            }
          result.add(dsr);
        }
        return result;
    }
	
	// helper
	private Alert readAlertFromRS(ResultSet rs) throws SQLException
	{
		String hostName = rs.getString("mgm_hostName_firstString");
		String serverIp = rs.getString("mgm_serverIP_firstString");
		double score = rs.getDouble("confidence");
		String hideMask = rs.getString("hide_mask");
		
		return new Alert(Alert.TYPE_BEHAVIORAL, score, hostName, serverIp, hideMask);
	}
		
	private Connection connect(String dbName)
	{
		return connect(dbName, Config.getDBUserName(), Config.getDBPassword());
	}
	
	private Connection connect(String dbName, String user, String password) throws DaoException
	{
		String url = "jdbc:mysql://" + Config.getDBUrl() + "/" + dbName;
		try {
			return DriverManager.getConnection(url, user, password);
		} catch (SQLException e) {

			logger.severe("Cannot connect to DB: " + dbName + " see log");
			throw new DaoException(e);
		}
	}
	
	private void disconnect(Connection con) throws DaoException
	{
		try {
			con.close();
		} catch (SQLException e) {
			logger.info("db exception, see log. msg: " + e.getMessage());
			throw new DaoException(e);
		}
	}

	
	public static void main(String[] args) throws Exception{
		CNCDao dao = new CNCDao();
		List<DSRData> dataList = dao.getDsrsInfo("smallFlow.pcap");
		
		
		for(DSRData dsr : dataList)
		{
			dao.getDsrScore(dsr);
		}
		

	}
}
