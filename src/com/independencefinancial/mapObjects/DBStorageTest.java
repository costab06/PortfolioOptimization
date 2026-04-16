package com.bcfinancial.mapObjects;


import java.io.*;
import java.util.*;
import java.text.*;

public class DBStorageTest {
    
    static final String DRIVER_CLASS_NAME = "com.mysql.jdbc.Driver";
    static final String DRIVER_ID_STRING = "mysql";
    static final String PORT = "";
    static final String HOSTNAME = "192.168.1.151";
    static final String USER_NAME = "dbuser";
    static final String PASSWD = "dbuser";
    static final String DATABASE_NAME = "test";
    
    public static void main (String[] argz) throws Exception {
	ObjectStorage storage = new MySQLObjectStorage (DRIVER_CLASS_NAME,
							DRIVER_ID_STRING,
							PORT,
							HOSTNAME,
							USER_NAME,
							PASSWD,
							DATABASE_NAME);
	ObjectStorer storer = new ReflectionStorer (storage);
	
	processDir(storer,"a");
	
    }
    
    
    
    private static void processDir(ObjectStorer storer, String dir) {
	Equity equity = null;
	// open file, read in lines, process each line
	File[] files = getFiles("z:\\Financial\\prophet_data\\"+dir);
	
	
	if (files != null) {
	    for (int i=0;i<files.length;i++) {
		File infile = files[i];
		String fileName = infile.getName();
		String[] pieces = fileName.split("\\.");
		String tableName = (pieces[0]);
		System.out.println("tableName: "+tableName);
		
		
		
		try {
		    BufferedReader in = new BufferedReader(new FileReader(infile));
		    String  line;
		    while((line=in.readLine()) != null) { 
			String[] values = line.split("\\,");
			DateFormat df = new SimpleDateFormat("yyMMdd");
			Date d = df.parse(values[0]);
			double open = Double.parseDouble(values[1]);
			double high = Double.parseDouble(values[2]);
			double low = Double.parseDouble(values[3]);
			double close = Double.parseDouble(values[4]);
			int volume = Integer.parseInt(values[5]);
			equity = new Equity(d,open,high,low,close,volume);	
			System.out.println("Storing: "+equity);
			storer.put (d, equity);
			System.out.println("Storing: "+equity+" SUCCESS");
		    }
		} catch (Exception e) {
		    System.out.println("Exception: "+e);
		}
		
	    }
	} else {
	    System.out.println("No files");
	}
    }
    
    
    private static File[] getFiles(String dirName) {
	
	File dir = new File(dirName);
	
	
	// The list of files can also be retrieved as File objects
	File[] files =null;
	
	// This filter only returns directories
	FileFilter fileFilter = new FileFilter() {
		public boolean accept(File file) {
		    return file.isFile();
		}
	    };
	files = dir.listFiles(fileFilter);
	return files;
    }
}


class Equity {
    private Date date;
    private Double open;
    private Double high;
    private Double low;
    private Double close;
    private Integer volume;
    
    public Equity() {}

    public Equity (Date date, double open, double high, double low, double close, int volume) {
	this.date = date;
	this.open = new Double(open);
	this.high = new Double(high);
	this.low = new Double(low);
	this.close = new Double(close);
	this.volume = new Integer(volume);
    }
    
    public String toString() {
	return "| "+date+" | "+open+" | "+high+" | "+low+" | "+close+" | "+volume+" |";
    }
    
}


	





















