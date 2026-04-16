package com.bcfinancial.sqlWindow;


import java.awt.*;
import java.awt.event.*;

public class WindowHandler extends WindowAdapter
{
    Application theParent;

    public WindowHandler(Application parent)
    {
        theParent = parent;
    }

    public void windowClosing(WindowEvent event)
    {
        theParent.requestClose( );
    }
}
