package com.bcfinancial.servlet;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.BindException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.rmi.RMISecurityManager;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class AbstractServer extends Thread {
	private static final Logger logger = Logger.getLogger(AbstractServer.class.getName());

    public static final int DEFAULT_START_PORT_NUMBER = 8320;
    private static final int MAX_PORT_NUMBER = 65535;

    private static final String JAVA_RMI_SERVER_CODEBASE_PROPERTY_NAME = "java.rmi.server.codebase";

    protected final ServerSocket serverSocket;

	public AbstractServer(int startPort, boolean daemon, String rmiServerCodebaseTemplate) throws IOException {
	    if (System.getSecurityManager() == null) {
	        System.setSecurityManager(new RMISecurityManager());
	    }
		serverSocket = bindToNextAvailablePort(startPort, rmiServerCodebaseTemplate);


		System.out.println("Binding to serverSocket: "+serverSocket);

		setDaemon(daemon);
	}
	
    protected ServerSocket bindToNextAvailablePort(int startPort, String rmiServerCodebaseTemplate) throws IOException {
    	ServerSocket serverSocket = null;
    	BindException bindException = null;
    	for (int port = startPort; port <= MAX_PORT_NUMBER; port++) { 
    		try {
    			logger.fine("Binding to port " + port);
    			serverSocket = new ServerSocket(port);
    			logger.fine("Bound to port " + port);
    			if (rmiServerCodebaseTemplate != null) {
    				String codebase = rmiServerCodebaseTemplate.replaceAll("\\$\\{port\\}", port + "");
    				System.setProperty(JAVA_RMI_SERVER_CODEBASE_PROPERTY_NAME, codebase);
				System.out.println("codebase: "+codebase);
    			}
    			break;
    		} catch (BindException e) {
    			bindException = e;
    			logger.fine("Could not bind to port " + port + ".");
    		}
    	}
    	if (serverSocket == null && bindException != null) {
    		throw bindException;
    	}
    	return serverSocket;
    }

    public void run() {
    	while (true) {
    		try {
    			Socket socket = serverSocket.accept();
    			RequestThread requestThread = new RequestThread(socket);
    			requestThread.start();
    		} catch (IOException e) {
    			logger.log(Level.WARNING, "Exception while waiting for a connection.", e);
    		}
    	}
    }

	protected void sendError(OutputStream out, String statusCode, String reasonPhrase) throws IOException {
		String response = "HTTP/1.0 " + statusCode + " " + reasonPhrase + "\r\n\r\n";
		logger.fine("Response: " + response);
		out.write(response.getBytes());
		out.flush();
		out.close();
	}

    protected abstract void handleRequest(String method, String path, OutputStream out) throws IOException;
    
	public class RequestThread extends Thread {
		private static final int READ_TIMEOUT = 30 * 1000;
		
		private final Socket socket;
		public RequestThread(Socket socket) {
			this.socket = socket;
		    setDaemon(true);		
		}
		public void run() {
			try {
				socket.setSoTimeout(READ_TIMEOUT);				
				BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
				BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
				String request = in.readLine();
				if (request == null) {
					logger.warning("Null request.");
					sendError(out, "400", "Bad Request");
					return; 
				}
				logger.fine("Request: " + request);
				int i = request.indexOf(' ');
				if (i == -1) {
					sendError(out, "400", "Bad Request");
					return; 
				}
				String method = request.substring(0, i);
				String path = request.substring(i + 1);
				i = path.indexOf(' ');
				if (i == -1) {
					sendError(out, "400", "Bad Request");
					return;
				}
				path = path.substring(0, i);
				path = URLDecoder.decode(path, "UTF-8");
				handleRequest(method, path, out);
			} catch (IOException e) {
				logger.log(Level.WARNING, "Exception while processing request.", e);
			} finally {
				try {
					socket.close();
				} catch (IOException e) {
					logger.log(Level.FINE, "Exception while closing socket.", e);
				}
			}
		}
		
	}
    

}
