package com.bcfinancial.servlet;

import java.io.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.bcfinancial.persistenceManager.*;
import com.bcfinancial.portfolio.*;
import com.bcfinancial.user.*;



public class GetPortfolioMessageProcessor implements CommandProcessor {
    
    final static boolean DEBUG=true;
    PersistenceManagerImpl persistenceManager = null;
    
    public void init() throws Exception {
	try {
	    persistenceManager = new PersistenceManagerImpl(Portfolio.class);
	} catch (Exception e) {
	    System.out.println("Exception creating persistence manager for Portfolio class: "+e);
	    throw e;
	}
    }
    
    public void process(ObjectInputStream in, ObjectOutputStream out) throws Exception {
	Portfolio portfolio = null;
	
	// read the serialized user and portfolio name data from applet
	User receivedUser = (User) in.readObject();
	String portfolioName = (String) in.readObject();
	in.close();

	if (DEBUG) {
	    System.out.println("receivedUser: "+receivedUser);
	    System.out.println("portfolioName: "+portfolioName);
	}
	
	try {
	    
	    // get the portfolios from the system
	    portfolio = (Portfolio) persistenceManager.select("portfolios",portfolioName);
	    
	    if (portfolio != null) {
		// send the user back to the applet
		
		System.out.println("Sending matching portfolio to applet...");
		out.writeObject(portfolio);
	    } else {
		out.writeObject(new Exception("portfolio does not exist."));
	    }

	} catch (Exception e) {
	    System.out.println("Exception: problem retrieving portfolio: "+e.toString());
	    e.printStackTrace();
	    out.writeObject(e);	
	} finally {
	    out.flush();
	    out.close();
	    System.out.println("Data transmission complete.");
	}
    }
}