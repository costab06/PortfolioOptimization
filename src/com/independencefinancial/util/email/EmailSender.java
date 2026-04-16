package com.bcfinancial.util.email;

import java.io.*;
import java.net.InetAddress;
import java.util.Properties;
import java.util.Date;
import javax.mail.*;
import javax.mail.internet.*;
import java.util.*;


public class EmailSender {

    
    public static void postMail( String to, String from, String subject, String message ) throws MessagingException
    {
	boolean debug = true;
	
	//Set the host smtp address
	Properties props = new Properties();
	props.put("mail.smtp.host", "smtp.gmail.com");
	props.put("mail.smtp.auth", "true");

	
	// create some properties and get the default Session
	Session session = Session.getInstance(props, null);
	session.setDebug(debug);
	
	// create a message
	Message msg = new MimeMessage(session);
	
	// set the from and to address
	InternetAddress addressFrom = new InternetAddress(from);
	msg.setFrom(addressFrom);
	msg.setRecipients(Message.RecipientType.TO,
			  InternetAddress.parse(to, false));
	
	
	// Optional : You can also set your custom headers in the Email if you Want
	msg.addHeader("MyHeaderName", "myHeaderValue");
	
	// Setting the Subject and Content Type
	msg.setSubject(subject);
	msg.setContent(message, "text/plain");
	Transport.send(msg);
    }
}