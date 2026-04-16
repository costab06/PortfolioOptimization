package com.bcfinancial.user;

import com.bcfinancial.persistenceManager.*;
import com.bcfinancial.portfolio.*;
import java.util.*;

public class CreateUser {
    
    public static void main(String[] args) {
	User u;
	Portfolio p;
	System.out.println("creating user");
	try {


	    if (args.length < 3) {
		System.out.println("usage: CreateUser <userName> <password> <emailAddr> [alpha] [beta] [lambda]");
		System.exit(1);
	    }

	    // create the user
	    double alpha  = args.length > 3 ? Double.parseDouble(args[3]) : 0.88;
	    double beta   = args.length > 4 ? Double.parseDouble(args[4]) : 0.88;
	    double lambda = args.length > 5 ? Double.parseDouble(args[5]) : 2.25;
	    User user = new User (args[0],args[1],args[2],new String[0],alpha,beta,lambda);
	    
	    // Persistence Manager
	    PersistenceManagerImpl upm = new PersistenceManagerImpl(User.class);
	    
	    // Persist user
	    
	    upm.insert("users",user.getUsername(),user);
	    System.out.println("stored user");
	    
	    
	    // Verify the user
	    
	    u = (User)upm.select("users",user.getUsername());
	    System.out.println("retrieved: "+u);
	    
	} catch (Exception e) {
	    System.out.println("Exception: "+e);
	    e.printStackTrace();
	}
	
    }

    
}
