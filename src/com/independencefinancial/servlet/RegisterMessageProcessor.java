package com.bcfinancial.servlet;

import java.io.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.bcfinancial.persistenceManager.*;
import com.bcfinancial.user.*;
import com.bcfinancial.utility.*;


public class RegisterMessageProcessor implements CommandProcessor {
    
    PersistenceManagerImpl userPersistenceManager = null;
    
    public void init() throws Exception {
	try {
	    userPersistenceManager = new PersistenceManagerImpl(User.class);
	} catch (Exception e) {
	    System.out.println("Exception creating persistenceManagers for User: "+e);
	    throw e;
	}
	
    }
    
    public void process(ObjectInputStream in, ObjectOutputStream out) throws Exception {
	User aUser = null;
	
	// read the serialized user data from applet
	User receivedUser = (User) in.readObject();
	in.close();
	
	try {
	    
	    // create the user
	    User user = (User) userPersistenceManager.select("users",receivedUser.getUsername());
	    if (user == null) {
		System.out.println("User not in system, creating...");
		userPersistenceManager.insert("users",receivedUser.getUsername(),receivedUser);
		user = (User) userPersistenceManager.select("users",receivedUser.getUsername());

		out.writeObject(user);
	    } else {
		System.out.println("User in system, sending exception...");
		out.writeObject(new Exception("Username already in system."));
	    }
	    
	} catch (Exception e) {
	    System.out.println("Exception: problem registering user: "+e.toString());
	    e.printStackTrace();
	    out.writeObject(e);	
	} finally {
	    
	    out.flush();
	    out.close();
	    System.out.println("Data transmission complete.");
	}
    }


}