package com.bcfinancial.sqlWindow;


import java.awt.*;
import java.awt.event.*;
import java.sql.*;

class SQLWindow extends Frame
implements Application, ActionListener, Runnable
{
    Panel      topPanel    = new Panel( );
    Panel      sourcePanel = new Panel( );
    Panel      query1Panel = new Panel( );
    Panel      query2Panel = new Panel( );
    Panel      mainPanel   = new Panel( );
    Panel      bottomPanel = new Panel( );
    Panel      buttonPanel = new Panel( );

    Font       topFont    = new Font("Courier", Font.BOLD,  12);
    Font       resultFont = new Font("Courier", Font.PLAIN, 12);
    Font       printFont  = new Font("Courier", Font.PLAIN, 12);

	Label      theSource = new Label( );
	TextField  theQuery1 = new TextField( );
	TextField  theQuery2 = new TextField( );
	TextArea   theResult = new TextArea(15, 80);
	Button     btnOpen   = new Button("Open Connection");
	Button     btnQuery1 = new Button("Execute Query #1");
	Button     btnQuery2 = new Button("Execute Query #2");
	Button     btnPrint  = new Button("Print");
	Button     btnQuit   = new Button("Quit");
	TextField  theStatus = new TextField( );

	FocusHandler theFocusHandler = new FocusHandler( );

	String theDataSource;
	String theUser;
	String thePassword;
	String theQuery;

    Connection        theConnection;
	Statement         theStatement;
	ResultSet         theResultSet;
	ResultSetMetaData theMetaData;

    Thread theThread = null;

    public static void main(String args[])
    {
        new SQLWindow( ).init( );
    }

	public void init( )
	{
	    setTitle("SQLWindow");

		topPanel.setFont(topFont);
		theResult.setFont(resultFont);

		add("North",  topPanel);
		add("Center", mainPanel);
		add("South",  bottomPanel);

		topPanel.setLayout(new GridLayout(4, 1));
		topPanel.add(sourcePanel);
		topPanel.add(query1Panel);
		topPanel.add(query2Panel);

		sourcePanel.setLayout(new BorderLayout( ));
		sourcePanel.add("West",   new Label("Source: "));
		sourcePanel.add("Center", theSource);

		query1Panel.setLayout(new BorderLayout( ));
		query1Panel.add("West",    new Label("Query #1:"));
		query1Panel.add("Center", theQuery1);

		query2Panel.setLayout(new BorderLayout( ));
		query2Panel.add("West",    new Label("Query #2:"));
		query2Panel.add("Center", theQuery2);

		mainPanel.setLayout(new BorderLayout( ));
		mainPanel.add("Center", theResult);

		bottomPanel.setLayout(new BorderLayout( ));
		bottomPanel.add("North", buttonPanel);
		bottomPanel.add("South", theStatus);

		buttonPanel.setLayout(new GridLayout(1, 5));
		buttonPanel.add(btnOpen);
		buttonPanel.add(btnQuery1);
		buttonPanel.add(btnQuery2);
		buttonPanel.add(btnPrint);
		buttonPanel.add(btnQuit);

		theResult.setEditable(false);
		theStatus.setEditable(false);

		addWindowListener(new WindowHandler(this));

		theQuery1.addActionListener(this);
		theQuery2.addActionListener(this);
		btnOpen  .addActionListener(this);
		btnQuery1.addActionListener(this);
		btnQuery2.addActionListener(this);
		btnPrint .addActionListener(this);
		btnQuit  .addActionListener(this);

		theQuery1.addFocusListener(theFocusHandler);
		theQuery2.addFocusListener(theFocusHandler);
		theResult.addFocusListener(theFocusHandler);

		while (!openConnection( )) ; // null statement

		pack( );
		show( );
	}

	public void run( )
	{
        try
        {
            String command = theQuery;
			theResult.setText("");
            if (theStatement != null) theStatement.close( );
            if (theResultSet != null) theResultSet.close( );

			theStatement = theConnection.createStatement();
			theResultSet = theStatement.executeQuery(command);

			DisplayableResultSet dsr =
			  new DisplayableResultSet(theResultSet);
			theResult.setText(dsr.getString( ));
        }
        catch (SQLException e)
        {
            handleError(e);
        }
        catch (NoClassDefFoundError err) { ; }
	}

    public void actionPerformed(ActionEvent event)
    {
        theStatus.setText("Status: OK");
        Object source = event.getSource( );
        if (source == btnOpen)         openConnection( );
        else if (source == btnQuery1)  startQuery(theQuery1.getText( ));
        else if (source == btnQuery2)  startQuery(theQuery2.getText( ));
        else if (source == theQuery1)  startQuery(theQuery1.getText( ));
        else if (source == theQuery2)  startQuery(theQuery2.getText( ));
        else if (source == btnPrint)   printResult( );
        else if (source == btnQuit)    requestClose( );
    }

    synchronized public void startQuery(String query)
    {
        killThread( );
        theThread = new Thread(this);
        theQuery = query;
        theThread.start( );
    }

    public void killThread( )
    {
        if (theThread != null && theThread.isAlive( ))
        {
            if (theStatement != null)
            {
                try
                {
                    theStatement.cancel( );
                }
                catch (SQLException sql) { ; }
            }
            theThread.stop( );
            theThread = null;
        }
    }

	public boolean openConnection( )
	{
	    killThread( );
        LoginDialog theLoginDialog = null;
        try
        {
            theSource.setText("");
            theResult.setText("");
            theLoginDialog
              = new LoginDialog("SQLWindow: "
                + "Please select the data source:",
                "jdbc:mysql://192.168.1.151/bcfinancial", this, true);
            theLoginDialog.setVisible(true);
            if (theLoginDialog.getStatus( ))
            {
                theSource.setText(theLoginDialog.getURL( ));
                Class.forName ("com.mysql.jdbc.Driver");
                if (theConnection != null) theConnection.close( );
        		theConnection =
        		  DriverManager.getConnection(theLoginDialog.getURL( ),
        		    theLoginDialog.getUser( ),
        		    theLoginDialog.getPassword( ));
            }
        	theLoginDialog.dispose( );
        	return true;
        }
        catch (Throwable t)
        {
            handleError(t);
        }
      	if (theLoginDialog != null) theLoginDialog.dispose( );
    	return false;
	}

	public void printResult( )
	{
	    new PrintReport( ).print(theResult.getText( ), printFont, 30, 20);
	}

    public void requestClose( )
    {
        setVisible(false);
        try
        {
            if (theConnection != null) theConnection.close( );
        }
        catch (SQLException ex)  { ; }
        System.exit(0);
    }

    public void handleError(Throwable t)
    {
        theStatus.setText("Error: " + t.getMessage( ));
        t.printStackTrace( );
    }
}

