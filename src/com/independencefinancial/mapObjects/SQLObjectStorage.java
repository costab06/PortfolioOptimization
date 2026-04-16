package com.bcfinancial.mapObjects;

import java.io.*;
import java.sql.*;
import java.util.*;

public abstract class SQLObjectStorage implements ObjectStorage {

  protected static final boolean DEBUG = false;
  protected static final boolean DEBUG_2 = false;

  // sqlEncode these entries ahead of time
  protected static final String KEY_COLUMN_NAME = sqlEncode ("OBJECT_STORAGE_KEY_0000");
  protected static final String KEY_TABLE_NAME = sqlEncode ("OBJECT_STORAGE_KEY_TABLE_0000");
  protected static final String CLASS_COL_NAME = sqlEncode ("CLASS");

  protected String driverClassName;
  protected String driverIDString;
  protected String port;
  protected String hostname;
  protected String username;
  protected String password;
  protected String databaseName;

  protected Connection connection;
  
  protected SQLObjectStorage (String driverClassName,
                              String driverIDString,
                              String port,
                              String hostname,
                              String username,
                              String password,
                              String databaseName) throws ClassNotFoundException,
                                                          IllegalAccessException,
                                                          InstantiationException,
                                                          SQLException {
    this.driverClassName = driverClassName;
    this.driverIDString = driverIDString;
    this.port = port;
    this.hostname = hostname;
    this.username = username;
    this.password = password;
    this.databaseName = databaseName;
    System.out.println("db class: "+driverClassName);
    Class.forName (driverClassName).newInstance (); // workaround for broken Java impls
    if (DEBUG_2) {
      PrintWriter pw = new PrintWriter (System.out);
      DriverManager.setLogWriter (pw);
    }
    getConnection ();
    // create table KEY_TABLE_NAME to map key->tablename
    StorageFields descriptor = new StorageFields (sqlDecode (KEY_TABLE_NAME));
    // create "CLASS_TABLE" column
    descriptor.addField (sqlDecode (CLASS_COL_NAME), String.class, null);
    createTable (descriptor); 
  }

  /*
           * * * *   ObjectStorage method implementations
   */
  
  public synchronized void put (Object key, StorageFields object) throws IOException {
    if (key == null) return;
    if (DEBUG) System.out.println (this + "::put: key is '" + key + "', object is " + object);  
    if (DEBUG_2) System.out.println (object);
    String keyString = key.toString ();
    try {
      removeEntries (keyString); // always remove old entries under key, if they exist
      if (object != null) {
        createTable (object);
        storeValuesToTable (keyString, object);
      }
    } catch (SQLException ex) {
      if (DEBUG) {
        System.err.println (this + "::put: caught exception... ");
        System.err.println ("SQLException: " + ex.getMessage ());
        System.err.println ("SQLState:     " + ex.getSQLState ());
        System.err.println ("VendorError:  " + ex.getErrorCode ());
      }
      throw new IOException (ex.getMessage ());
    }
  }

  public synchronized RetrievalFields get (Object key) throws IOException {
    if (key == null) return null;
    if (DEBUG) System.out.println (this + "::get: key is '" + key + "'");
    RetrievalFields fields = null;
    try {
      String keyString = key.toString ();
      String classTable = getClassTableForKey (keyString);
      fields = getEntryForKeyInTable (keyString, classTable);
    } catch (SQLException ex) {
      if (DEBUG) {
        System.err.println (this + "::get: caught exception... ");
        System.err.println ("SQLException: " + ex.getMessage ());
        System.err.println ("SQLState:     " + ex.getSQLState ());
        System.err.println ("VendorError:  " + ex.getErrorCode ());
      }
      throw new IOException (ex.getMessage ());
    }
    return fields;
  }

  /*
           * * * *   implementation methods
   */
    
  // subclasses define driver-specific connection tasks and set the connection variable
  protected abstract void getConnection () throws SQLException;

  // subclasses define database-specific type mapping
  protected abstract String getTypeString (Class type);
  
  // subclasses define type information when reconstructing objects and sqlDecode () strings
  protected abstract Object getObjectFromResultSet (ResultSet rs,
                                                    int colNum,
                                                    String colName,
                                                    int jdbcType) throws SQLException;
  
  // override to change max key length
  protected int getMaxKeyLength () {
    return 255;
  }

  // override to change String representation of a given type (e.g., Float)
  protected String getValueString (Object value) {
    return value.toString ();
  } 
  
  protected void createTable (StorageFields object) throws SQLException {
    // CREATE TABLE TABLE_NAME (COL1_NAME COL1_TYPE, COL2_NAME COL2_TYPE, ...);
    Statement statement = null;
    try {
      StringBuffer sqlBuffer = new StringBuffer ();
      String tableName = sqlEncode (object.getClassName ()); // get rid of . in classname
      if (doesTableExist (tableName)) return;
      sqlBuffer.append ("CREATE TABLE " +
                        tableName +
                        " (" +
                        KEY_COLUMN_NAME +
                        " VARCHAR(" +
                        getMaxKeyLength () +
                        "), "); 
      Iterator fieldNames = object.getFieldNames ();   
      while (fieldNames.hasNext ()) {
        String fieldName = (String) fieldNames.next ();
        Class type = object.getType (fieldName);
        String typeString = getTypeString (type);
        sqlBuffer.append (sqlEncode (fieldName) + " " + typeString);
        if (fieldNames.hasNext ()) sqlBuffer.append (", ");
      }
      sqlBuffer.append (")");
      if (DEBUG) System.out.println (this + "::createTable: sending: " + sqlBuffer);
      statement = connection.createStatement ();
      statement.executeUpdate (sqlBuffer.toString ());
    } finally {
      if (statement != null) {
        try {
          statement.close ();
        } catch (SQLException ignored) {
        }
      }
    }
  }

  protected boolean doesTableExist (String tableName) throws SQLException {
    if (DEBUG) System.out.println (this + "::doesTableExist: checking...");
    ResultSet rs = null;
    try {
      DatabaseMetaData dbmd = connection.getMetaData ();
      String catalog = null; // drop catalog name from the selection criteria
      String schemaPattern = ""; // retrieves those without a schema 
      String tableNamePattern = tableName; // look only for this table  
      String [] types = null; // returns all types
      rs = dbmd.getTables (catalog,
                           schemaPattern,
                           tableNamePattern,
                           types);

      if (DEBUG_2) {
        ResultSetMetaData meta = rs.getMetaData ();
        int n = meta.getColumnCount ();
        for (int i = 1; i <= n; ++ i)
          System.out.print (meta.getColumnLabel (i) + "  |  ");
        System.out.print ("\n");
        while (rs.next ()) {
          for (int i = 1; i <= n; ++ i) {
            String s = rs.getString (i);
            System.out.print (s + "  |  ");
          }
          System.out.print ("\n");
        }
        System.out.println ("");
      }
      rs.beforeFirst ();
      boolean next = rs.next ();
      if (DEBUG) System.out.println (this + "::doesTableExist: returning: " + next);
      return next;
    } finally {
      if (rs != null) {
        try {
          rs.close ();
        } catch (SQLException ignored) {
        }
      }
    }
  }

  protected void removeEntries (String keyString) throws SQLException {
    Statement selectStatement = null;
    Statement updateStatement = null;
    ResultSet rs = null;
    try {
      StringBuffer sqlBuffer = new StringBuffer ("SELECT " +
                                                 CLASS_COL_NAME +
                                                 " FROM " +
                                                 KEY_TABLE_NAME +
                                                 " WHERE " +
                                                 KEY_COLUMN_NAME + " = '" +
                                                 sqlEncode (keyString) + "'");
      if (DEBUG) System.out.println (this + "::removeEntries: sending: " + sqlBuffer);
      selectStatement = connection.createStatement ();
      updateStatement = connection.createStatement ();

      rs = selectStatement.executeQuery (sqlBuffer.toString ());

      rs.beforeFirst ();
      // remove from any class tables that contain key exists
      while (rs.next()) {
        String classTable = rs.getString (1);
        if (classTable != null) {
          if (DEBUG) System.out.println (this + "::removeEntries: key entry in " + classTable);
          sqlBuffer = new StringBuffer ();
          sqlBuffer.append ("DELETE FROM " +
                            classTable +
                            " WHERE " +
                            KEY_COLUMN_NAME + " = '" +
                            sqlEncode (keyString) + "'");
          if (DEBUG) System.out.println (this + "::removeEntries: sending: " + sqlBuffer);
          updateStatement.executeUpdate (sqlBuffer.toString ());
        }
      }
      // remove key entry (ies) from key table
      sqlBuffer = new StringBuffer ();
      sqlBuffer.append ("DELETE FROM " +
                        KEY_TABLE_NAME +
                        " WHERE " +
                        KEY_COLUMN_NAME + " = '" +
                        sqlEncode (keyString) + "'");
      if (DEBUG) System.out.println (this + "::removeEntries: sending: " + sqlBuffer);
      updateStatement.executeUpdate (sqlBuffer.toString ());

      // *** add feature: drop table if there are zero entries
      
    } finally {
      if (rs != null) {
        try {
          rs.close ();
        } catch (SQLException ignored) {
        }
      }
      if (selectStatement != null) {
        try {
          selectStatement.close ();
        } catch (SQLException ignored) {
        }
      }
      if (updateStatement != null) {
        try {
          updateStatement.close ();
        } catch (SQLException ignored) {
        }
      }
    } 
  }
  
  protected void storeValuesToTable (String keyString, StorageFields object) throws SQLException {
    // INSERT INTO TABLENAME (COL1_NAME, COL2_NAME) VALUES ('COL1_VALUE', 'COL2_VALUE')
    Statement statement = null;
    try { 
      StringBuffer sqlBuffer = new StringBuffer ();
      String className = object.getClassName ();
      sqlBuffer.append ("INSERT INTO " +
                        sqlEncode (className) + " (" +
                        KEY_COLUMN_NAME + ", ");
      Iterator fieldNames = object.getFieldNames ();
      StringBuffer orderedValuesBuffer = new StringBuffer ("VALUES ('" +
                                                           sqlEncode (keyString) + "', ");
      while (fieldNames.hasNext ()) {
        String fieldName = (String) fieldNames.next ();
        sqlBuffer.append (sqlEncode (fieldName));
        if (fieldNames.hasNext ()) sqlBuffer.append (", ");
        Object value = object.getValue (fieldName);
        String valueString = getValueString (value);
        orderedValuesBuffer.append ("'" + sqlEncode (valueString));
        if (fieldNames.hasNext ()) orderedValuesBuffer.append ("', ");
      }
      sqlBuffer.append (")");
      orderedValuesBuffer.append ("')");
      sqlBuffer.append (" " + orderedValuesBuffer);
      if (DEBUG) System.out.println (this + "::storeValuesToTable: sending: " + sqlBuffer);
      statement = connection.createStatement ();
      statement.executeUpdate (sqlBuffer.toString ());
      // put entry in KEY table to map key to table name for the object's class
      sqlBuffer = new StringBuffer ("INSERT INTO " +
                                    KEY_TABLE_NAME + " (" + 
                                    KEY_COLUMN_NAME + ", " +
                                    CLASS_COL_NAME +
                                    ") VALUES ('" +
                                    sqlEncode (keyString) + "', '" + 
                                    sqlEncode (className) + "')");
      if (DEBUG) System.out.println (this + "::storeValuesToTable: sending: " + sqlBuffer);
      statement = connection.createStatement ();
      statement.executeUpdate (sqlBuffer.toString ()); 
    } finally {
      if (statement != null) {
        try {
          statement.close ();
        } catch (SQLException ignored) {
        }
      }
    }
  }

  protected String getClassTableForKey (String keyString) throws SQLException {
    // return first class table record for key in key table (there should be only one...)
    Statement statement = null;
    ResultSet rs = null;
    try {
      StringBuffer sqlBuffer = new StringBuffer ("SELECT " +
                                                 CLASS_COL_NAME +
                                                 " FROM " +
                                                 KEY_TABLE_NAME +
                                                 " WHERE " +
                                                 KEY_COLUMN_NAME + " = '" +
                                                 sqlEncode (keyString) + "'");
      if (DEBUG) System.out.println (this + "::getClassTableForKey: sending: " + sqlBuffer);
      statement = connection.createStatement ();
      rs = statement.executeQuery (sqlBuffer.toString ());
      rs.beforeFirst ();
      rs.next ();
      String classTable = rs.getString (1);
      if (classTable == null) {
        if (DEBUG) System.out.println (this + "::getClassTableForKey: no value for '" + keyString + "' found in key table, so returning null.");
      }
      return classTable; 
    } finally {
      if (rs != null) {
        try {
          rs.close ();
        } catch (SQLException ignored) {
        }
      }
      if (statement != null) {
        try {
          statement.close ();
        } catch (SQLException ignored) {
        }
      }
    }
  }

  protected RetrievalFields getEntryForKeyInTable (String keyString,
                                                   String classTable) throws SQLException {
    if (classTable == null) return null;
    // find first entry for key in class table (there should be only one...)
    Statement statement = null;
    ResultSet rs = null;
    try {
      String className = sqlDecode (classTable);
      RetrievalFields fields = new RetrievalFields (className);
      if (DEBUG) System.out.println (this + "::getEntryForKeyInTable: class name: " + className);
      StringBuffer sqlBuffer = new StringBuffer ();
      sqlBuffer.append ("SELECT * FROM " +
                        classTable +
                        " WHERE " +
                        KEY_COLUMN_NAME + " = '" +
                        sqlEncode (keyString) + "'");
      if (DEBUG) System.out.println (this + "::getEntryForKeyInTable: sending: " + sqlBuffer);
      statement = connection.createStatement ();
      rs = statement.executeQuery (sqlBuffer.toString ());
      rs.next (); // advance to the first entry
      ResultSetMetaData meta = rs.getMetaData ();
      int n = meta.getColumnCount ();      
      for (int colNum = 1; colNum <= n; ++ colNum) { 
        String colName = meta.getColumnLabel (colNum);
        int jdbcType = meta.getColumnType (colNum);
        if (DEBUG) System.out.println (this + "::getEntryForKeyInTable: found colName: '" + colName + "' of JDBC type: " + jdbcType);
        if (!colName.equals (KEY_COLUMN_NAME)) {
          Object object = getObjectFromResultSet (rs, colNum, colName, jdbcType);
          String field = sqlDecode (colName);          
          if (DEBUG) System.out.println (this + "::getEntryForKeyInTable: field '" + field + "' value is: " + object);
          fields.addField (field, object);
        }
      }
      return fields;
    } finally {
      if (rs != null) {
        try {
          rs.close ();
        } catch (SQLException ignored) {
        }
      }
      if (statement != null) {
        try {
          statement.close ();
        } catch (SQLException ignored) {
        }
      }
    }
  }
  
  
  protected void finalize () {
    try {
      connection.close ();
    } catch (Exception ex) {
      if (DEBUG) {
        System.out.println (this + "::finalize: caught exception: ");
        ex.printStackTrace ();
      }
    }
    if (DEBUG) System.out.println (this + "::finalize completed.");
  }
  
  protected static String sqlEncode (String txt) {
    // escape char is '_' in conformance with table and col naming constraints
    char escape_char = '_';
    StringBuffer e = new StringBuffer ();
    for (int i = 0; i < txt.length (); i ++) {
      char c = txt.charAt (i);
      if (c < 16)
        e.append (escape_char + "0" + Integer.toString (c, 16));
      else if ((c < 32) || (c > 127) || (".%^_'\"".indexOf (c) >= 0))
        e.append (escape_char + Integer.toString (c, 16));
      else 
        e.append (c);
    }
    return e.toString ();
  }

  protected static String sqlDecode (String txt) {
    // escape char is '_' in conformance with table and col naming constraints
    char escape_char = '_';
    StringBuffer d = new StringBuffer ();
    for (int i = 0; i < txt.length (); i ++) {
      char c = txt.charAt (i);
      if (c == escape_char) {
        String hex = txt.substring (i + 1, i + 3);
        char dec = (char) Integer.parseInt (hex, 16);
        d.append (dec);
        i += 2;
      }
      else
        d.append (c); 
    }
    return d.toString ();
  }
}






