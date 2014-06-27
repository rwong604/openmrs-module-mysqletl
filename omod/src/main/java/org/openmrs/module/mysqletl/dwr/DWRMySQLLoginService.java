package org.openmrs.module.mysqletl.dwr;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openmrs.api.APIException;
import org.openmrs.module.mysqletl.tools.HiveClient;
import org.openmrs.module.mysqletl.tools.MySQLClient;
import org.openmrs.module.mysqletl.tools.SSHClient;

public class DWRMySQLLoginService {
	
	public List<String> loginMySQL(LoginParams params) throws APIException  {
		List<String> arrayList = new ArrayList<String>();
		try{ 
			Class.forName("com.mysql.jdbc.Driver");
			String connectionURL = "jdbc:mysql://"+params.gethost()+":"+params.getport()+"/";
			Connection con = DriverManager.getConnection (connectionURL , params.getuser(), params.getpass());
			ResultSet rs = con.getMetaData().getCatalogs();
			while (rs.next()) {
			    arrayList.add(rs.getString("TABLE_CAT"));
			}
			MySQLClient.MySQLParameters(params); //Setting MySQL Parameters for Later Use
			return arrayList;
		}
		catch(Exception e){
		    return null;
	    }
	}
	public List<String> getTables(LoginParams params,String db_name) throws APIException, ClassNotFoundException, SQLException  {
	    List<String> arrayList = new ArrayList<String>();
		try{ 
			Class.forName("com.mysql.jdbc.Driver");
			String connectionURL = "jdbc:mysql://"+params.gethost()+":"+params.getport()+"/"+db_name;
			Connection con = DriverManager.getConnection (connectionURL , params.getuser(), params.getpass());
			DatabaseMetaData md = con.getMetaData();
			ResultSet rsTable = md.getTables(null, null, "%", null);
			while (rsTable.next()) {
				arrayList.add(rsTable.getString(3));
			}
		    return arrayList;
		}
		catch(Exception e){
		    return null;
	    }
	}
	public List<String> getColumns(LoginParams params,String db_name,String table_name) throws APIException, ClassNotFoundException, SQLException  {
	    List<String> arrayList = new ArrayList<String>();
		try{ 
			Class.forName("com.mysql.jdbc.Driver");
			String connectionURL = "jdbc:mysql://"+params.gethost()+":"+params.getport()+"/"+db_name;
			Connection con = DriverManager.getConnection (connectionURL , params.getuser(), params.getpass());
			Statement stmt = null;
			stmt = con.createStatement();
			ResultSet rs = stmt.executeQuery("SELECT * FROM "+table_name);
			ResultSetMetaData rsmd = rs.getMetaData();
			int columnCount = rsmd.getColumnCount();

			for (int i = 1; i < columnCount + 1; i++ ) {
				arrayList.add(rsmd.getColumnName(i));
			}

		    return arrayList;
		}
		catch(Exception e){
		    return null;
	    }
	}
	public String goTransform(LoginParams params,String serverType,String db_name,String table_name,List<String> column_list) throws APIException  {
		try{ 
			HiveClient.HiveParameters(params); //Set Hive Parameters
			//Setting Connection to MySQL
			Class.forName("com.mysql.jdbc.Driver");
			String connectionURL = "jdbc:mysql://"+MySQLClient.gethost()+":"+MySQLClient.getport()+"/";
			Connection con = DriverManager.getConnection (connectionURL , MySQLClient.getuser(), MySQLClient.getpass());
			//get table list
			List<String> tableListWithDuplicates = new ArrayList<String>();
			for(String column : column_list){
				tableListWithDuplicates.add(column.substring(0,column.indexOf('.', column.indexOf('.')+1)));
			}
			List<String> tableList = new ArrayList<String>(new HashSet<String>(tableListWithDuplicates));
			Statement stmt = null;
			stmt = con.createStatement();
			//Create Fresh Temporary database
			String dropFreshQuery = "drop database if exists "+db_name;
			stmt.execute(dropFreshQuery);
			String create_query = "create database if not exists "+db_name;
			stmt.execute(create_query);
			//Create extracted data in form of table
			String query = "CREATE TABLE "+db_name+"."+table_name+" AS SELECT "+column_list.toString().substring(1, column_list.toString().length()-1)+" FROM "+tableList.toString().substring(1, tableList.toString().length()-1);
			stmt.execute(query);
			//Set SSH Connection Parameters
			SSHClient.SetSSHParameters(params.gethost(),params.getuser(),params.getpass());
			//Get Own IP Address which where we are client to machine running Hive and SSH
			String Host = SSHClient.getIpAddress();
			//create database in hive
			//HiveClient.createDatabase(params.gethost(), params.getport(), "", "", db_name);
			//Sqoop Import Data
			SSHClient.sqoopImport(Host,MySQLClient.getport(),MySQLClient.getuser(),MySQLClient.getpass(),db_name,table_name,db_name);
			//Drop Temporary created database
			String dropQuery = "drop database "+db_name;
			stmt.execute(dropQuery);
		    return "Success";
		}
		catch(Exception e){
		    return "Failed";
	    }
	}
}
