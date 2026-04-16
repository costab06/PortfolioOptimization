package com.bcfinancial.persistenceManager;

import java.util.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.lang.NoSuchMethodException;
import java.lang.IllegalAccessException;
import java.lang.reflect.InvocationTargetException;
import java.io.*;

public class PersistenceManagerImpl implements PersistenceManager {
    private static final int CONN_TIMEOUT = 10000;

    boolean DEBUG=false;
    Class clazz = null;
    ConnectionPool connectionPool = null;
    Connection conn = null;

    // Configuration loaded from application.properties at construction time.
    // Fallback values are preserved so the class still works without the file.
    String jdbcDriver;
    String jdbcUrl;
    String jdbcDatabase;
    String jdbcUser;
    String jdbcPw;
    int connectionPoolSize;

    private static java.util.Properties loadAppProperties() {
        java.util.Properties props = new java.util.Properties();
        try (java.io.InputStream is =
                PersistenceManagerImpl.class.getClassLoader()
                    .getResourceAsStream("application.properties")) {
            if (is != null) {
                props.load(is);
            }
        } catch (java.io.IOException e) {
            System.err.println("Could not load application.properties, using hardcoded defaults: " + e);
        }
        return props;
    }

    private void initConfig() {
        java.util.Properties props = loadAppProperties();
        // MariaDB driver replaces the deprecated com.mysql.jdbc.Driver
        jdbcDriver  = props.getProperty("db.driver",   "org.mariadb.jdbc.Driver");
        jdbcUrl     = props.getProperty("db.url",      "jdbc:mariadb://192.168.1.151/bcfinancial");
        jdbcDatabase= props.getProperty("db.name",     "bcfinancial");
        jdbcUser    = props.getProperty("db.user",     "dbuser");
        jdbcPw      = props.getProperty("db.password", "dbuser");
        String poolSizeStr = props.getProperty("db.pool.size",
                String.valueOf(Runtime.getRuntime().availableProcessors()));
        connectionPoolSize = Integer.parseInt(poolSizeStr);
    }

    SqlClauseGenerator generator = null;


    // holds prepared statements for tables that have been accessed before
    boolean usePreparedStatements=true;
    Hashtable selectStatements = new Hashtable(); 
    Hashtable insertStatements = new Hashtable();
    Hashtable deleteStatements = new Hashtable();
    
    Vector allFields = new Vector();
    TypeField keyTypeField = null;
    Vector ordinaryTypeFields = new Vector();
    String keyFieldName = null;
    
    public PersistenceManagerImpl(Class clazz, boolean usePreparedStatements) throws Exception{
	this(clazz);
	this.usePreparedStatements = usePreparedStatements;
    }
    
    public PersistenceManagerImpl(Class clazz) throws Exception {

        initConfig();

	this.clazz = clazz;
	try {
	    this.keyFieldName = (String)invokeStaticMethod(clazz,"getKeyFieldName",new Class[0],new Object[0]);
	} catch (Exception e) {
	    error("Exception invoking getKeyFieldName for "+clazz+": "+e);
	    throw e;
	}
	

        try {
	    Class.forName(jdbcDriver).newInstance();
	} catch (Exception e) {
	    error("Exception loading Driver "+jdbcDriver+": "+e);
	    throw e;
	}

	try {
	    connectionPool = ConnectionPool.newInstance(jdbcUrl,jdbcUser,jdbcPw,connectionPoolSize);
	} catch (Exception e) {
	    error("Exception creating connection pool using: "+jdbcDriver+", "+jdbcUrl+", "+jdbcUser+", "+jdbcPw+", "+connectionPoolSize+": "+e);
	    throw e;
	}
	
	
	// collect all fields in all superclasses of the object
	
	for (Class climber = clazz; climber != null; climber = climber.getSuperclass()) {
	    Field[] fields = climber.getDeclaredFields();
	    for (int i=0; i<fields.length; i++)
		allFields.add(fields[i]);
	}
	
	// process the fields
	
	for (int j=0; j<allFields.size();j++) {
	    Field field = (Field)allFields.elementAt(j);

	    // find the key field - always a type field

	    if (field.getName().equals(keyFieldName)) {

		keyTypeField = TypeField.makeTypeField(field);

		continue;
	    }



	    // the other fields can be one of ArrayField, TypeField, or SerializableField, try each and catch the exceptions

	    // we can check if it is an array
	    Class fieldType = field.getType();
	    if (fieldType.isArray()) {

		// returns null if the field should not be persisted - is transient, etc
		ArrayField arrayField = ArrayField.makeArrayField(field);
		if (arrayField != null) {
		    ordinaryTypeFields.add(arrayField);
		}
		continue;
	    } 
	    
	    
	    // see if it can be stored as a type in the database, and if not then just serialize the sucker
	    
	    else {
		try {
		    // returns null if the field should not be persisted - is transient, etc
		    TypeField typeField = TypeField.makeTypeField(field);
		    if (typeField != null) {
			ordinaryTypeFields.add(typeField);
		    }
		    continue;
		} catch(CannotPersistException c) {
		    // ignore - not a problem, try serializing it
		}
		
		try {
		    SerializableField serializableField = SerializableField.makeSerializableField(field);
		    // returns null if the field should not be persisted - is transient, etc
		    if (serializableField != null) {
			ordinaryTypeFields.add(serializableField);
		    }
		    continue;
		} catch (CannotPersistException c) {
		    // bad - not an array, cannot be stored at a type, and cannot be serialized
		    error(c.toString());
		    throw c;
		}
	    }
	}
	
	// create the generator for future use
	
	try {
	    
	    // generate the sql statements
	    generator = new SqlClauseGenerator(clazz,keyTypeField,ordinaryTypeFields);

	    
	    
	} catch (Exception e) {
	    error("Exception creating the generator with: \n"+clazz+"\n"+keyTypeField+"\n"+ordinaryTypeFields+"\n"+e);
	    throw(e);
	}
    }
    

    
    public void setUsePreparedStatements(boolean use) {
	usePreparedStatements = use;
    }
    
    public boolean getUsePreparedStatements() {
	return usePreparedStatements;
    }
    
    private String getPreparedSelectStatement(String tableName, SqlClauseGenerator generator) {
	return "select "+generator.columnNameList() + " from " + jdbcDatabase + "." + sqlEncode(tableName) + " where " + generator.keyName() + " = ? ";
    }
    
    private String getSelectStatement(String tableName, SqlClauseGenerator generator, Object key) {
	return "select "+generator.columnNameList() + " from " + jdbcDatabase + "." + sqlEncode(tableName) + " where " + generator.keyName() + " = '"+getStringRepresentation(key)+"'";
    }
    
    private String getPreparedInsertStatement(String tableName, SqlClauseGenerator generator) {
	int values = generator.numColumns();
	
	
	String s = "insert into " + jdbcDatabase + "." + sqlEncode(tableName) + " ("+generator.columnNameList()+")  values (";
	for (int i = 0; i < values;i++) {
	    s+="?";
	    if (i < values-1)
		s+=", ";
	}
	s+=")";
	return s;
    }

    private String getInsertStatement(String tableName, SqlClauseGenerator generator, Object[] values) {

	int num = generator.numColumns();
	String s = "insert into " + jdbcDatabase + "." + sqlEncode(tableName) + " ("+generator.columnNameList()+")  values (";
	
	// add the string representations of the values
	for (int i = 0; i < num;i++) {
	    s+="'"+getStringRepresentation(values[i])+"'";
	    
	    if (i < num-1)
		s+=", ";
	}
	s+=")";
	return s;
    }

    
    private String getPreparedDeleteStatement(String tableName, SqlClauseGenerator generator) {
	return "delete from " + jdbcDatabase + "." + sqlEncode(tableName) + " where " + generator.keyName() + " = ? ";
    }

    private String getDeleteStatement(String tableName, SqlClauseGenerator generator, Object key) {
	return "delete from " + jdbcDatabase + "." + sqlEncode(tableName) + " where " + generator.keyName() + " = "+"'"+getStringRepresentation(key)+"'";
    }
    

    public void createTable(String tableName) {
	Connection conn = null;
	Statement  createTableStatement = null;
	String createTableString = null;

	try {
	    
	    try {
		createTableString = (String)invokeStaticMethod(clazz,"getCreateTableString",(new Class[] {String.class}),new String[] {sqlEncode(tableName)});
	    } catch (Exception e) {
		System.err.println("Exception invoking getCreateTableString for "+clazz+": "+e);
	    }
	    
	    conn = connectionPool.getConnection(CONN_TIMEOUT);
	    createTableStatement = conn.createStatement();
	    
	    createTableStatement.execute(createTableString);
	} catch (SQLException e) {
	    System.err.println("Exception creating table: "+tableName+": "+e);
	    // ignore create table exception - table aleady exists
	} finally {
	    try {
		createTableStatement.close();
	    } catch (SQLException e) { //ignore
	    }
	    try {
		conn.close();
	    } catch (Exception e) { //ignore
	    }
	}
    }
    
    
    
    private String getStringRepresentation(Object o) {
	Class objectType = null;
	DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
	// manipulate the date objects, the default string representation doesn't work
	objectType = o.getClass();
	if (objectType == java.util.Date.class) {
	    return (df.format(o));
	} else {
	    return o.toString();
	}
	
    }

    
    public Persistable select(String tableName, Object key) throws Exception{
	Connection conn = null;
	ResultSet resultSet = null;
	Persistable persistable = null;
	

	String s = null;
	
	if (usePreparedStatements) {
	    if (DEBUG)
		System.out.println("using prepared statements");

	    PreparedStatement selectStatement = null;

	    synchronized(selectStatements) {
		// check for a prepared statement for this table
		selectStatement = (PreparedStatement) selectStatements.get(tableName);
	    }
	    if (selectStatement == null) {
		
		if (DEBUG)
		    System.out.println("no prepared statement in map");

		// create a prepared statement for this table and save it for future use
		s = getPreparedSelectStatement(tableName, generator);

		if (DEBUG)
		    System.out.println("created prepared statement: "+s);
		

		try {
		    conn = connectionPool.getConnection(CONN_TIMEOUT);
		    selectStatement = conn.prepareStatement(s);
		    synchronized (selectStatements) {
			selectStatements.put(tableName,selectStatement);
		    }
		} catch (SQLException e) {
		    error("Exception creating prepared statement for select: "+e);
		    throw e;
		} finally {
		    try {
			conn.close();
		    } catch (Exception e) { //ignore
		    }
		}
		
		
	    }

	    try {
		selectStatement.setObject(1,key);
		if (DEBUG)
		    System.out.println("about to executeQuery: "+selectStatement);
		
		resultSet = selectStatement.executeQuery();
		
		if (DEBUG) {
		    showResultSet(resultSet);
		}
		
		persistable = createPersistable(resultSet);
	    } catch (Exception e) {
		error("Exception selecting row and creating persistable: "+e);
		throw e;
		
	    } finally {
		try {
		    resultSet.close();
		} catch (Exception e) { // ignore
		}
	    }
	} else {  // no prepared statements
	    // ONLY PREPARED STATEMENTS WORK AT THE MOMENT, CANT WRITE AND READ THE
	    // SERIALIZED BYTE ARRAYS FOR ARRAYTYPE AND SERIALIZEDTYPE CLASSES CORRECTLY


	    Statement selectStatement = null;

	    s = getSelectStatement(tableName,generator,key);

	    
	    if (DEBUG)
		System.out.println("not using prepared statements, select: "+s);
	    
	    try {
		conn = connectionPool.getConnection(CONN_TIMEOUT);
		selectStatement = conn.createStatement();

		resultSet = selectStatement.executeQuery(s);

		if (DEBUG) {
		    showResultSet(resultSet);
		}
		persistable =  createPersistable(resultSet);
	    } catch (Exception e) {
		System.err.println("Exception selecting row and creating persistable: "+e);
		throw e;
	    } finally {
		try {
		    selectStatement.close();
		} catch (Exception e) { //ignore
		}
		try {
		    resultSet.close();
		} catch (Exception e) { // ignore
		}
		try {
		    conn.close();
		} catch (Exception e) {  // ignore
		}
	    }
	    
	}

	return persistable;
    }




    private Persistable createPersistable(ResultSet resultSet) throws Exception {
	Persistable persistable = null;
	TypeField field = null;
	ArrayField aField = null;
	SerializableField sField = null;
	String fieldName = null;
	Object columnValue = null;
	Object[] aColumnValue = null;
	Object sColumnValue = null;
	
	if (resultSet == null) {
	    System.err.println("ResultSet for select is null");
	    return null;
	}
	
	if (resultSet.first()) {
	    
	    Class[] c = new Class[0];
	    Constructor constructor = clazz.getConstructor(c);
	    Object[] o = new Object[0];
	    persistable = (Persistable) constructor.newInstance(o);
	    
	    field = (TypeField)keyTypeField;
	    fieldName = keyTypeField.getName();
	    columnValue = resultSet.getObject(fieldName);
	    
	    if (DEBUG) {
		System.out.println("setting "+field.getName()+" to "+columnValue+" on "+persistable);
	    }
	    
	    field.set(persistable,columnValue);
	    
	    
	    for (int i=0;i<ordinaryTypeFields.size();i++) {
		Object obj = ordinaryTypeFields.elementAt(i);
		if (obj instanceof TypeField) {
		    field = (TypeField) obj;
		    fieldName = field.getName();
		    columnValue = resultSet.getObject(fieldName);
		    
		    
		    if (DEBUG) {
			System.out.println("setting "+field.getName()+" to "+columnValue+" on "+persistable);
		    }
		    
		    
		    field.set(persistable,columnValue);
		    
		} else if (obj instanceof ArrayField) {
		    // arrays are serialized at the moment, but this should change so
		    // leave this as a separate code section
		    aField = (ArrayField) obj;
		    fieldName = aField.getName();
		    
		    
		    byte[] bytes = resultSet.getBytes(fieldName);
		    
		    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		    ObjectInputStream ois = new ObjectInputStream(bais);
		    aColumnValue = (Object[]) ois.readObject();
		    ois.close();
		    
		    
		    if (DEBUG) {
			System.out.println("setting "+aField.getName()+" to "+aColumnValue+" on "+persistable);
		    }
		    
		    
		    aField.set(persistable,aColumnValue);
		} else if (obj instanceof SerializableField) {
		    sField = (SerializableField) obj;
		    fieldName = sField.getName();
		    
		    
		    byte[] bytes = resultSet.getBytes(fieldName);
		    
		    ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
		    ObjectInputStream ois = new ObjectInputStream(bais);
		    sColumnValue = (Object) ois.readObject();
		    ois.close();
		    
		    
		    if (DEBUG) {
			System.out.println("setting "+sField.getName()+" to "+sColumnValue+" on "+persistable);
		    }
		    
		    
		    sField.set(persistable,sColumnValue);
		}
	    }
	} else {
	    persistable = null;
	}
	
	return persistable;
    }
    
    
    
    public void insert(String tableName,Object key, Persistable persistable) throws SQLException {
	Connection conn = null;
	String s = null;
	TypeField field = null;
	Object value = null;
	ArrayField aField = null;
	Object[] aValue = null;
	SerializableField sField = null;
	Object sValue = null;
	
	if (usePreparedStatements) {
	    PreparedStatement insertStatement = null;

	    synchronized (insertStatements) {
		// check for a prepared statement for this table
		insertStatement = (PreparedStatement) insertStatements.get(tableName);
	    }
	    if (insertStatement == null) {
		// create a prepared statement for this table and save it for future use
		s = getPreparedInsertStatement(tableName, generator);

		try {
		    conn = connectionPool.getConnection(CONN_TIMEOUT);
		    insertStatement = conn.prepareStatement(s);
		    synchronized (insertStatements) {
			insertStatements.put(tableName,insertStatement);
		    }
		} catch (SQLException e) {
		    System.err.println("Exception creating prepared statement for insert: "+e);
		    throw e;
		} finally {
		    try {
			conn.close();
		    } catch (Exception e) {  //ignore
		    }
		}
	    }
	    // bind the variables of the prepared statement
	    
	    try {
		// key field first
		field = (TypeField)keyTypeField;	
		value = field.get(persistable);

		insertStatement.setObject(1,value);
		Object o;
		for (int i=0;i<ordinaryTypeFields.size();i++) {
		    o = ordinaryTypeFields.elementAt(i);
		    if (o instanceof TypeField) {
			field = (TypeField) o;
			value = field.get(persistable);

			insertStatement.setObject(i+2,value);
		    } else if (o instanceof ArrayField) {
			aField = (ArrayField) o;
			aValue = (Object[]) aField.get(persistable);
			
			
			
			try {
			    //arrays are serialized at the moment
			    ByteArrayOutputStream baos = new ByteArrayOutputStream();
			    ObjectOutputStream oos = new ObjectOutputStream(baos);
			    oos.writeObject(aValue);
			    oos.close();
			    byte[] bytes = baos.toByteArray();


			    ByteArrayInputStream bytesIS = 
				new ByteArrayInputStream(bytes);

			    
			    insertStatement.setBinaryStream(i+2,bytesIS,bytes.length);


			    
			} catch (IOException e) {
			    System.out.println("Exception creating byte array for: "+aValue+" "+e);
			}
		    } else if (o instanceof SerializableField) {
			sField = (SerializableField) o;
			sValue = (Object) sField.get(persistable);
			
			
			
			try {

			    ByteArrayOutputStream baos = new ByteArrayOutputStream();
			    ObjectOutputStream oos = new ObjectOutputStream(baos);
			    oos.writeObject(sValue);
			    oos.close();
			    byte[] bytes = baos.toByteArray();


			    ByteArrayInputStream bytesIS = 
				new ByteArrayInputStream(bytes);

			    
			    insertStatement.setBinaryStream(i+2,bytesIS,bytes.length);


			if (DEBUG) {
			    System.out.println("inserting: "+ new String(bytes));
			}

			
			} catch (IOException e) {
			    System.out.println("Exception creating byte array for: "+sValue+" "+e);
			}
		    }
		}
		// execute the insert
		insertStatement.executeUpdate();
	    } catch (SQLException e) {
		System.err.println("Exception inserting row:"+e);
		throw e;
	    }
	    
	    
	} else {  // no prepared statements
	    // ONLY PREPARED STATEMENTS WORK AT THE MOMENT, CANT WRITE AND READ THE
	    // SERIALIZED BYTE ARRAYS FOR ARRAYTYPE AND SERIALIZEDTYPE CLASSES CORRECTLY

	    Object[] values = new Object[ordinaryTypeFields.size()+1];
	    Statement insertStatement = null;
	    
	    field = (TypeField)keyTypeField;	
	    values[0] = field.get(persistable);
	    
	    for (int i=0;i<ordinaryTypeFields.size();i++) {
		Object o = ordinaryTypeFields.elementAt(i);
		if (o instanceof TypeField) {
		    field = (TypeField)o;
		    values[i+1] = field.get(persistable);
		}
		else {
		    aField = (ArrayField)o;
		    aValue = (Object[])aField.get(persistable);
		    
		    
		    byte[] bytes = null;
		    
		    try {
			//arrays are serialized at the moment
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			ObjectOutputStream oos = new ObjectOutputStream(baos);
			oos.writeObject(aValue);
			oos.flush();
			oos.close();
			bytes = baos.toByteArray();
		    } catch (IOException e) {
			System.out.println("Exception creating byte array for: "+aValue+" "+e);
		    }
		    
		    String aFieldStr = new String(bytes);
		    values[i+1] = aFieldStr;
		}
	    }
	    
	    s = getInsertStatement(tableName, generator, values);
	    if (DEBUG) {
		System.out.println("Inserting: "+s);
	    }
	    
	    try {
		conn = connectionPool.getConnection(CONN_TIMEOUT);
		insertStatement = conn.createStatement();

		insertStatement.executeUpdate(s);

	    } catch(SQLException e) {
		System.err.println("Exception inserting row:"+e);
		throw e;
	    }  finally {
		try {
		    insertStatement.close();
		} catch (SQLException e) {  // ignore
		}
		try {
		    conn.close();
		} catch (Exception e) {  //ignore
		}
	    }
	}
    }




    


    public void update(String tableName, Object key, Persistable persistable) throws SQLException {
	delete(tableName,key);
	insert(tableName,key,persistable);
    }
	    
    
    public void delete(String tableName, Object key) throws SQLException {
	String s = null;
	ResultSet resultSet = null;
	

	if (usePreparedStatements) {
	    PreparedStatement deleteStatement = null;
	    synchronized (deleteStatements) {
	    // check for a prepared statement for this table
	    deleteStatement = (PreparedStatement) deleteStatements.get(tableName);
	    }
	    if (deleteStatement == null) {
		// create a prepared statement for this table and save it for future use
		s = getPreparedDeleteStatement(tableName, generator);

		try {
		    conn = connectionPool.getConnection(CONN_TIMEOUT);
		    deleteStatement = conn.prepareStatement(s);
		    synchronized (deleteStatements) {
			deleteStatements.put(tableName,deleteStatement);
		    }
		} catch (SQLException e) {
		    error("Exception creating prepared statement for delete: "+e);
		    throw e;
		} finally {
		    try {
			conn.close();
		    } catch (Exception e) {  //ignore
		    }
		}
	    }
	    
	    try {
		deleteStatement.setObject(1,key);
		deleteStatement.executeUpdate();
	    } catch (SQLException e) {
	    error("Exception deleting row:"+e);
	    throw e;
	    } finally {
		try {
		    resultSet.close();
		} catch (Exception e) { //ignore
		}

	    }
	} else {  // no prepared statements

	    s = getDeleteStatement(tableName,generator,key);
	    System.out.println("delete statement: "+s);
	    Statement deleteStatement = null;

	    try {
		conn = connectionPool.getConnection(CONN_TIMEOUT);
		deleteStatement = conn.createStatement();

		deleteStatement.executeUpdate(s);
	    } catch (SQLException e) {
		System.err.println("Exception deleting row: "+e);
		throw e;
	    } finally {
		try {
		    deleteStatement.close();
		} catch (Exception e) { //ignore
		}
		try {
		    resultSet.close();
		} catch (Exception e) { //ignore
		}
		try {
		    conn.close();
		} catch (Exception e) {  //ignore
		}
		
	    }
	    
	}
    }
    

    private void showResultSet(ResultSet rs) {
	
	/*
	  System.out.println("-- results ---");
	  //if the rowUpdateCount !=-1 then the result object
	  //is from a preparedStatement that has updated records
	  int rowUpdateCount=sqlResult.getRowUpdateCount();
	  if(rowUpdateCount!=-1){
	  System.out.println("Number of rows updated="+rowUpdateCount);
	  return;
	  }
	  //dump out the results to the console
	  String [] cols=sqlResult.getCols();
	  Object [][] data=sqlResult.getData();
	  StringBuffer buf=new StringBuffer();
	  for(int c=0;c<cols.length;c++){
	  buf.append(cols[c]);
	  if(c<cols.length-1){
	  buf.append("\t");
	  }
	  }
	  //dump the column headers
	  System.out.println(buf.toString());
	  //process the row data
	  for(int r=0;r<data.length;r++){
	  buf=new StringBuffer();
	  for(int c=0;c<data[r].length;c++){
	  buf.append(data[r][c]);
	  if(c<cols.length-1){
	  buf.append("\t");
	  }
	  }
	  //print the row out
	  System.out.println(buf.toString());
	  }
	  }
	*/
	
    }
    


    private Object invokeStaticMethod(Class clazz,String methodName,Class[]argTypes,Object[] args) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
	Method method = clazz.getDeclaredMethod(methodName,argTypes);
	return method.invoke(null,args);
    }
    
    


    
     protected static String sqlEncode (String txt) {
    // escape char is '_' in conformance with table and col naming constraints
    char escape_char = '_';
    StringBuffer e = new StringBuffer ();
    for (int i = 0; i < txt.length (); i ++) {
      char c = txt.charAt (i);
      if (c < 16)
        e.append (escape_char + "0" + Integer.toString (c, 16));
      else if ((c < 32) || (c > 127) || (".%^'\"".indexOf (c) >= 0))
	  //      else if ((c < 32) || (c > 127) || (".%^_'\"".indexOf (c) >= 0))
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

    private void debug(String s) {
	if (DEBUG)
	    System.out.println(s);
    }

    private void error(String s) {
	System.err.println(s);
    }

}
