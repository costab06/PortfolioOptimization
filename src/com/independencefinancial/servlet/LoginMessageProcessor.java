package com.bcfinancial.servlet;

import java.io.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.bcfinancial.persistenceManager.*;
import com.bcfinancial.user.*;


public class LoginMessageProcessor implements CommandProcessor {
    
    final static boolean DEBUG=true;
    PersistenceManagerImpl persistenceManager = null;
    
    public void init() throws Exception {
	try {
	    persistenceManager = new PersistenceManagerImpl(User.class);
	} catch (Exception e) {
	    System.out.println("Exception creating persistence manager for User class: "+e);
	    throw e;
	}
    }
    
    public void process(ObjectInputStream in, ObjectOutputStream out) throws Exception {
	User aUser = null;
	
	// read the serialized user data from applet
	User receivedUser = (User) in.readObject();
	in.close();

	if (DEBUG) {
	    System.out.println("receivedUser: "+receivedUser);
	}
	
	try {
	    // get the user from the system
	    User user = (User) persistenceManager.select("users",receivedUser.getUsername());
	    
	    if (user != null) {
		// send the user back to the applet
		
		System.out.println("Sending matching user to applet...");
		out.writeObject(user);
	    } else {
		out.writeObject(new Exception("username does not exist."));
	    }
	} catch (Exception e) {
	    System.out.println("Exception: problem retrieving user: "+e.toString());
	    e.printStackTrace();
	    out.writeObject(e);	

	} finally {
	    out.flush();
	    out.close();
	    System.out.println("Data transmission complete.");
	}
    }
}