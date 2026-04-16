package com.bcfinancial.mapObjects;


import java.io.*;
import java.sql.*;

public class MySQLObjectStorage extends SQLObjectStorage {

  public MySQLObjectStorage (String driverClassName,
                             String driverIDString,
                             String port,
                             String hostname,
                             String username,
                             String password,
                             String databaseName) throws ClassNotFoundException,
                                                         IllegalAccessException,
                                                         InstantiationException,
                                                         SQLException {
    super (driverClassName,
           driverIDString,
           port,
           hostname,
           username,
           password,
           databaseName);
  }

/*
  
  URL form: jdbc:mysql://[hostname][:port]/dbname[?param1=value1][&param2=value2]...

  PARAMETER - EXPLANATION - DEFAULT
  
  user - The user to connect as - none
  password - The password to use when connecting - none
  autoReconnect - should the driver attempt to re-connect if the connection dies? (true/false) - false
  maxReconnects - if autoReconnect is enabled, how many times should the driver attemt to reconnect? - 3
  initialTimeout - if autoReconnect is enabled, the initial time to wait between re-connects (seconds) - 2
  maxRows - The maximum number of rows to return (0 means return all rows) - 0
  useUnicode - should the driver use Unicode character encodings when handling strings? (true/false) - false
  characterEncoding - if useUnicode is true, what character encoding should the driver use when dealing with strings? - none

*/

  protected void getConnection () throws SQLException {
    String connectionString = "jdbc:" +
                               driverIDString +
                               "://" +
                               hostname +
                               (port.equals ("") ? "" : ":") +
                               port +
                               "/" +
                               databaseName +
                               "?user=" +
                               username +
                               "&password=" +
                               password;
    connection = DriverManager.getConnection (connectionString);
  }

  protected String getTypeString (Class type) {
    if (type == boolean.class)
      return "BIT";
    else if (type == byte.class)
      return "TINYINT"; // 1 byte; 
    else if (type == short.class)
      return "SMALLINT"; // 2 bytes
    else if (type == int.class)
      return "INT"; // 4 bytes
    else if (type == long.class)
      return "BIGINT"; // 8 bytes
    else if (type == float.class)
      return "FLOAT"; // 4 bytes
    else if (type == double.class)
      return "DOUBLE"; // 8 bytes
    else if (type == char.class)
      return "CHAR"; 
    else if (type == String.class)
      return "VARCHAR(255)";
    else if (type == Date.class)
	return "DATE";
    else return "VARCHAR(255)"; // anything else will wind up as String 
  }
  
  protected Object getObjectFromResultSet (ResultSet rs,
                                           int colNum,
                                           String colName,
                                           int jdbcType) throws SQLException {

      if (jdbcType == Types.BIT)
	  return new Boolean(rs.getBoolean(colNum));
      else if (jdbcType == Types.TINYINT)
	  return new Byte (rs.getByte (colNum));
      else if (jdbcType == Types.SMALLINT)
	  return new Short (rs.getShort (colNum));
      else if (jdbcType == Types.INTEGER)
	  return new Integer (rs.getInt (colNum));
      else if (jdbcType == Types.BIGINT)
	  return new Long (rs.getLong (colNum));
      else if (jdbcType == Types.FLOAT)
	  return new Float (rs.getFloat (colNum));
      else if (jdbcType == Types.DOUBLE)
	  return new Double (rs.getDouble (colNum)); 
      else if (jdbcType == Types.CHAR) {
	  String s = rs.getString (colNum);
	  return sqlDecode (s);
      } else if (jdbcType == Types.VARCHAR) {
	  String s = rs.getString (colNum);
	  return sqlDecode (s);
      } else if (jdbcType == Types.DATE)
	  return rs.getDate (colNum); 
      
      else {
	  String s = rs.getString (colNum);
	  return sqlDecode (s); // anything else will wind up as String
      }
  }
    
}












