package com.bcfinancial.servlet;

import java.io.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
//import org.tiling.computefarm.impl.javaspaces.util.ClasspathServer;

public class MessageHandlerServlet extends HttpServlet {
    

    /**
     * Creates a new servlet
     */
    public MessageHandlerServlet() {
	
	ClasspathServer server;
	
	// start a classserver to serve the computefarm
	
	try {
	    server = new ClasspathServer();
	    server.start();
	    try {
		Thread.sleep(1000);
	    } catch (InterruptedException ie) {
		// ignore
	    }

	    System.out.println("Started classpathServer: "+server);

	} catch (Exception e) {
	    System.err.println("Exception starting ClasspathServer: "+e);
	    System.exit(1);
	}

    }
    
    
    public void init() {
    }
    
    public void doPost(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException {
	
	try {
	    // get an input and output streams from the applet
	    ObjectInputStream inputFromApplet = new ObjectInputStream(request.getInputStream());
	    ObjectOutputStream outputToApplet = new ObjectOutputStream(response.getOutputStream());
	    // read the commandProcessor class file name and instantiate it
	    String commandProcessorName = (String) inputFromApplet.readObject();
	    Class commandProcessorClass = Class.forName(commandProcessorName);
	    CommandProcessor commandProcessor= (CommandProcessor)commandProcessorClass.newInstance();
	    
	    // have the commandProcess do it's thing
	    commandProcessor.init();
	    commandProcessor.process(inputFromApplet,outputToApplet);
	    
	} catch(Exception e) {
	    System.out.println("Exception: "+e);
	    e.printStackTrace();
	}
    }
}
