package com.bcfinancial.servlet;

import java.io.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.bcfinancial.persistenceManager.*;
import com.bcfinancial.portfolio.*;
import com.bcfinancial.user.*;

public class StorePortfolioMessageProcessor implements CommandProcessor {
    
    final static boolean DEBUG=true;
    PersistenceManagerImpl persistenceManager = null;
    
    public void init()  throws Exception {
	try {
	    persistenceManager = new PersistenceManagerImpl(Portfolio.class);
	} catch (Exception e) {
	    System.out.println("Exception getting persistence manager for Portfolio class: "+e);
	    throw e;
	}
    }
    
    public void process(ObjectInputStream in, ObjectOutputStream out) throws Exception {
	Portfolio aPortfolio = null;
	
	// read the serialized user and portfolio data from applet
	User receivedUser = (User) in.readObject();
	Portfolio portfolio = (Portfolio) in.readObject();
	in.close();
	
	if (DEBUG) {
	    System.out.println("receivedUser: "+receivedUser);
	    System.out.println("portfolio: "+portfolio);
	}
	
	try {
	    persistenceManager.insert("portfolios",new String(receivedUser.getUsername()+"|"+portfolio.getPortfolioName()),portfolio);
	    portfolio = (Portfolio) persistenceManager.select("portfolios",new String(receivedUser.getUsername()+"|"+portfolio.getPortfolioName()));
	    
	    if (portfolio != null) {
		// send the user back to the applet
		
		System.out.println("Sending matching portfolio to applet...");
		out.writeObject(portfolio);
	    }
	} catch (Exception e) {
	    System.out.println("Exception: problem storing portfolio: "+e.toString());
	    e.printStackTrace();
	    out.writeObject(e);
	} finally {
	    out.flush();
	    out.close();
	    System.out.println("Data transmission complete.");
	}
    }
}
