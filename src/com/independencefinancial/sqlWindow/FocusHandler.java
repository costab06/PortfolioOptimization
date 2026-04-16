package com.bcfinancial.sqlWindow;

import java.awt.*;
import java.awt.event.*;

public class FocusHandler extends FocusAdapter
{
    public void focusLost(FocusEvent event)
    {
        Object source = event.getSource( );
        if (source instanceof TextComponent)
        {
            TextComponent text = (TextComponent) source;
            int caret = text.getCaretPosition( );
            text.setSelectionStart(caret);
            text.setSelectionEnd  (caret);
        }
    }
}
