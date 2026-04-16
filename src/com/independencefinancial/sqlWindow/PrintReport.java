package com.bcfinancial.sqlWindow;


import java.awt.*;
import java.awt.event.*;
import java.util.*;

public class PrintReport extends Frame
{
    public void print(String text, Font font, int vmargin, int hmargin)
    {
        Properties props = null;
        PrintJob   pjob  = getToolkit( ).getPrintJob( this, "Java Report", props);
        int pageNo = 1;

        do
        {
            System.out.println("Printing page #" + pageNo++ + ".");
            Graphics pg = pjob.getGraphics();

            pg.setFont(font);
            pg.setColor(Color.black);

            int pixPerInch      = pjob.getPageResolution( );
            Dimension pageSize  = pjob.getPageDimension( );
            int width           = pageSize.width - pixPerInch;
            int height          = pageSize.height - pixPerInch;
            FontMetrics metrics = pg.getFontMetrics( );
            int lineHeight      = metrics.getHeight( ) + metrics.getLeading( );
            int linePos         = vmargin;

            if (text.charAt(text.length( ) - 1) != '\n') text += '\n';

            int lineEnd;
            while ((lineEnd = text.indexOf('\n')) >= 0)
            {
                String line = text.substring(0, lineEnd);
                if (linePos + lineHeight > height) break;
                pg.drawString(line, hmargin, linePos);
                linePos += lineHeight;
                if (lineEnd >= text.length( ) - 1)
                    text = "";
                else
                    text = text.substring(lineEnd + 1);
            }
            pg.dispose( );

        }
        while (text.length( ) > 0);
        System.out.println("Printing complete.");

        pjob.end();
    }
}
