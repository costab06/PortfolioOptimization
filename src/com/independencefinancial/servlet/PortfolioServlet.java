package com.bcfinancial.servlet;

import java.io.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


import com.bcfinancial.persistenceManager.*;
import com.bcfinancial.portfolio.*;
import com.bcfinancial.user.*;


public class PortfolioServlet extends HttpServlet {
    
    PersistenceManagerImpl portfolioPersistenceManager = null;
    /**
     * Creates a new servlet
     */
    public PortfolioServlet() {}
    

    public void init(){
	try {
	    portfolioPersistenceManager = new PersistenceManagerImpl(Portfolio.class);
	} catch (Exception e) {
	    System.out.println("Exception creating persistence manager for Portfolio class: "+e);
	}
    }
    
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
	doPost(request,response);
    } 
   
    public void doPost(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException {
	
	ObjectInputStream inputFromApplet = null;
	User aUser = null;
	
	try {
	    // get an input stream from the applet
	    inputFromApplet = new ObjectInputStream(request.getInputStream());
	    
	    // read the serialized user data from applet
	    User receivedUser = (User) inputFromApplet.readObject();
	    inputFromApplet.close();
	    
	    // get the portfolios from the system
	    String[] portfolioNames = receivedUser.getPortfolioNames();
	    Portfolio[] portfolios = new Portfolio[portfolioNames.length];
	    
	    for (int i=0;i<portfolioNames.length;i++) {
		portfolios[i] = (Portfolio) portfolioPersistenceManager.select("portfolios",portfolioNames[i]);
	    }
	    
	    // send the portfolio back to the applet
	    ObjectOutputStream outputToApplet = new ObjectOutputStream(response.getOutputStream());
	    
	    System.out.println("Sending user's portfolios to applet...");
	    outputToApplet.writeObject(portfolios); // could be null if user does not exist...
	    outputToApplet.flush();
	    
	    outputToApplet.close();
	    System.out.println("Data transmission complete.");
	    
	    
	} catch(Exception e) {
	    System.out.println("Exception: "+e);
	    // handle exception
	}
    }
}
