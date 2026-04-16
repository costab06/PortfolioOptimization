package com.bcfinancial.servlet;

import java.io.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.bcfinancial.persistenceManager.*;
import com.bcfinancial.portfolio.*;
import com.bcfinancial.user.*;



public class GetPortfolioOptimizationMessageProcessor implements CommandProcessor {
    
    final static boolean DEBUG=true;
    PersistenceManagerImpl persistenceManager = null;
    
    public void init() throws Exception {
	try {
	    persistenceManager = new PersistenceManagerImpl(PortfolioOptimization.class);
	} catch (Exception e) {
	    System.out.println("Exception creating persistence manager for PortfolioOptimization class: "+e);
	    throw e;
	}
    }
    
    public void process(ObjectInputStream in, ObjectOutputStream out) throws Exception {
	PortfolioOptimization portfolioOptimization = null;
	
	// read the portfolio optimization name data from applet
	String portfolioOptimizationName = (String) in.readObject();
	in.close();

	if (DEBUG) {
	    System.out.println("portfolioOptimizationName: "+portfolioOptimizationName);
	}
	
	try {
	    
	    // get the portfolio optimization from the system
	    portfolioOptimization = (PortfolioOptimization) persistenceManager.select("portfolio_optimizations",portfolioOptimizationName);
	    
	    if (portfolioOptimization != null) {
		// send back to the applet
		
		System.out.println("Sending matching portfolio optimization to applet...");
		out.writeObject(portfolioOptimization);
	    } else {
		out.writeObject(new Exception("portfolio optimizationdoes not exist."));
	    }

	} catch (Exception e) {
	    System.out.println("Exception: problem retrieving portfolio optimization: "+e.toString());
	    e.printStackTrace();
	    out.writeObject(e);	
	} finally {
	    out.flush();
	    out.close();
	    System.out.println("Data transmission complete.");
	}
    }
}