package com.bcfinancial.sqlWindow;

import java.awt.*;
import java.awt.event.*;

public class LoginDialog extends Dialog
implements ActionListener, Application
{
    String      theDefaultSource;
    Application theParent;

    Panel[]   thePanel    = new Panel[4];
    TextField theSource   = new TextField(35);
    TextField theUser     = new TextField( );
    TextField thePassword = new TextField( );
    Button    btnOK       = new Button("OK");
    Button    btnCancel   = new Button("Cancel");
    Button    btnQuit     = new Button("Quit");
    boolean   statusOK    = false;
    String theURL;

    public LoginDialog(String title, String source,
      Application parent, boolean showquit)
    {
        super((Frame) parent, title, true);
        theDefaultSource = source;
        theParent = parent;

        setFont(new Font("Courier", Font.PLAIN, 12));
        setLayout(new GridLayout(4, 1));
        for (int i = 0; i < 3; i++)
        {
            thePanel[i] = new Panel( );
            thePanel[i].setLayout(new BorderLayout( ));
        }

        thePanel[0].add("West",   new Label("URL:      ", Label.RIGHT));
        thePanel[0].add("Center", theSource);
        add(thePanel[0]);

        thePanel[1].add("West",   new Label("User:     ", Label.RIGHT));
        thePanel[1].add("Center", theUser);
        add(thePanel[1]);

        thePanel[2].add("West",   new Label("Password: ", Label.RIGHT));
        thePanel[2].add("Center", thePassword);
        add(thePanel[2]);

        thePanel[3] = new Panel( );
        thePanel[3].setLayout(new FlowLayout( ));
        thePanel[3].add(btnOK);
        thePanel[3].add(btnCancel);
        if (showquit) thePanel[3].add(btnQuit);
        add(thePanel[3]);

        btnOK.addActionListener(this);
        btnCancel.addActionListener(this);
        btnQuit.addActionListener(this);
        addWindowListener(new WindowHandler(this));

        pack( );
    }

    public boolean getStatus( )   { return statusOK; };
    public String  getURL( )      { return theSource.getText( ); }
    public String  getUser( )     { return theUser.getText( ); }
    public String  getPassword( ) { return thePassword.getText( ); }

    public void setVisible(boolean visible)
    {
        if (visible)
        {
            statusOK = false;
            theSource.setText(theDefaultSource);
            theUser.setText("");
            thePassword.setText("");

            Dimension screenSize
              = Toolkit.getDefaultToolkit().getScreenSize();
            int x = (screenSize.width/2) -(getSize( ).width/2);
            int y = (screenSize.height/3)-(getSize( ).height/2);

            setBounds(x,y,getSize( ).width, getSize( ).height);
        }
        super.setVisible(visible);
        if (visible)
        {
            theSource.requestFocus( );
        }
    }

    public void actionPerformed(ActionEvent event)
    {
        Object object = event.getSource( );
        if (object == btnOK) statusOK = true;
        setVisible(false);
        if (object == btnQuit) System.exit(0);
    }

    public void requestClose( )  { ; }
}