package com.bcfinancial.servlet;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.logging.Logger;

/**
 * A {@link ClasspathServer} is a very simple HTTP server for serving class files from
 * the classpath.
 */
public class ClasspathServer extends AbstractServer {
	private static final Logger logger = Logger.getLogger(ClasspathServer.class.getName());
	
	public ClasspathServer() throws IOException {
		this(DEFAULT_START_PORT_NUMBER);
	}
	
	public ClasspathServer(int startPort) throws IOException {
		this(startPort, true);
	}
	
	public ClasspathServer(int startPort, boolean daemon) throws IOException {
	    super(startPort, daemon, "http://" + InetAddress.getLocalHost().getHostName() + ":${port}/");
	}
	
	protected void handleRequest(String method, String path, OutputStream out) throws IOException {
		if (method.equals("GET") || method.equals("HEAD")) { 
			InputStream resource = getClass().getResourceAsStream(path);
			if (resource == null) {
				sendError(out, "404", "Not Found");
				return;
			} else {
			    ByteArrayOutputStream baos = new ByteArrayOutputStream();
			    write(resource, baos);
			    byte[] bytes = baos.toByteArray();
				out.write("HTTP/1.0 200 OK\r\n".getBytes());
				out.write("Content-Type: application/java\r\n".getBytes());
				out.write(("Content-Length: " + bytes.length + "\r\n").getBytes());
				out.write("\r\n".getBytes());
				if (method.equals("GET")) {
					out.write(bytes);
				}
				out.flush();
				out.close();
			}
		} else {
			sendError(out, "501", "Not Implemented");
			return; 
		}

	}

	private void write(InputStream is, ByteArrayOutputStream out) throws IOException {
		BufferedInputStream in = null;
		try {
			in = new BufferedInputStream(is);
			byte[] buf = new byte[4096];
			int bytesRead;
			while ((bytesRead = in.read(buf)) != -1) {
				out.write(buf, 0, bytesRead);
			}
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}

	public static void main(String[] args) throws IOException {
		new ClasspathServer(80, false).start();
	}
}
