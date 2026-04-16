package com.bcfinancial.user;

import com.bcfinancial.persistenceManager.*;
import com.bcfinancial.portfolio.*;
import java.util.*;
import java.io.*;

import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hssf.usermodel.HSSFRow;
public class UserAddPortfolio {
    
    public static void main(String[] args) {
	User u;
	Portfolio p;


	if (args.length < 3) {
	    System.out.println("usage: UserAddPortfolio <username> <portfolioname> <portfoliofile>");
	    System.exit(1);

	}
	// get the user
	String username = args[0];
	String portfolioname = args[1];
	String portfoliofile = args[2];
	
	System.out.println("username: "+username+" portfolioname: "+portfolioname+" portfoliofile: "+portfoliofile);
	
	
	try {
	    // Persistence Managers
	    PersistenceManagerImpl upm = new PersistenceManagerImpl(User.class);
	    PersistenceManagerImpl ppm = new PersistenceManagerImpl(Portfolio.class);

	    // get the user

	    User user = (User)upm.select("users",username);
	    if (user == null) {
		System.out.println("user with username: "+username+" not found");
		System.exit(1);
	    }
	    

	    // instantiate the portfolio
	    Portfolio portfolio = getPortfolio(username+"|"+portfolioname, portfoliofile);
	    

	    // persist the portfolio
	    
	    ppm.insert("portfolios",username+"|"+portfolioname,portfolio);
	    
	    
	    // update the user
	    user.addPortfolioName(username+"|"+portfolioname);

	    
	    // persist the user
	    
	    upm.update("users",user.getUsername(),user);
	    System.out.println("stored: "+user);
	    
	    
	} catch (Exception e) {
	    System.out.println("Exception: "+e);
	    e.printStackTrace();
	}
	
    }

    
    private static Portfolio getPortfolio(String portfolioname, String portfoliofile) {
	ArrayList symbolsList = new ArrayList();
	ArrayList weightsList = new ArrayList();
	
	try {
	    HSSFWorkbook workbook = new HSSFWorkbook(new FileInputStream(portfoliofile));
	    HSSFSheet sheet = workbook.getSheetAt(0);
	    
	    int rowno=0;
	    while(true) {
		HSSFRow row = sheet.getRow(rowno);
		// no more lines in the sheet then it returns null for the row
		if (row == null)
		    break;
		
		HSSFCell cell;
		// symbol
		cell = row.getCell((short)0);

		// could be past the end of the first column
		if (cell == null)
		    break;

		String sym = cell.getStringCellValue();
		System.out.println("adding: "+sym);
		symbolsList.add(sym);
		
		// percent
		cell = row.getCell((short)1);
		Double wei = new Double(cell.getNumericCellValue());
		System.out.println("adding: "+wei);
		weightsList.add(wei);
		rowno++;
	    }
	    
	} catch (Exception e) {
	    System.out.println("Exception processing portfolio: "+portfoliofile);
	    e.printStackTrace();
	    System.exit(1);
	}
	
	
	return new Portfolio(portfolioname,(String[]) symbolsList.toArray(new String[0]),
			     (Double[]) weightsList.toArray(new Double[0]));
	
    }
}
