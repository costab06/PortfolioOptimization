
package com.bcfinancial.servlet;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.FileReader;
import java.io.BufferedReader;


import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class PortfolioVAR extends HttpServlet {

    /**
     * Creates a new servlet
     */
    public PortfolioVAR() {


    }


    
    public void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
	doPost(request,response);
    }
    
    /**
     * Processes a POST request.
     * <P>
     * The chart.html page contains a form for generating the first request, after that
     * the HTML returned by this servlet contains the same form for generating subsequent
     * requests.
     *
     * @param request  the request.
     * @param response  the response.
     *
     * @throws ServletException if there is a servlet related problem.
     * @throws IOException if there is an I/O problem.
     */
    public void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        PrintWriter out = new PrintWriter(response.getWriter());
        try {
	    
            String param = request.getParameter("chart");
	    
            response.setContentType("text/html");
            out.println("<HTML>");
            out.println("<HEAD>");
	    
	    out.println("<script type=\"text/javascript\" language=\"JavaScript\">");
	    outputFile(out,"dateFunctions.js");
	    out.println("</script>");
	    
	    out.println("<TITLE>BCFinancial PortfolioVAR</TITLE>");
	    out.println("</HEAD>");
	    out.println("<BODY>");
            out.println("<H2>BCFinancial PortfolioVAR</H2>");
            out.println("<P>");
	    
	    
	    out.println("<script type=\"text/javascript\" language=\"JavaScript\">");
	    out.println("var calendarDate = getCalendarDate();");
	    out.println("var clockTime = getClockTime();");
	    out.println("document.write('Date is ' + calendarDate);");
	    out.println("document.write('<br>');");
	    out.println("document.write('Time is ' + clockTime);");
	    out.println("</script>");
            out.println("<P>");
	    

            out.println("Please choose a portfolio type:");

            out.println("<FORM ACTION=\"PortfolioVAR\" METHOD=POST>");
            String growthChecked = (param.equals("growth") ? " CHECKED" : "");
            String valueChecked = (param.equals("value") ? " CHECKED" : "");

            out.println("<INPUT TYPE=\"radio\" NAME=\"chart\" VALUE=\"growth\"" + growthChecked
                + "> Growth");
            out.println("<INPUT TYPE=\"radio\" NAME=\"chart\" VALUE=\"value\"" + valueChecked
                + "> Value");
            out.println("<P>");
            out.println("<INPUT TYPE=\"submit\" VALUE=\"Generate Chart\">");
            out.println("</FORM>");

            out.println("<P>");
            out.println("<IMG SRC=\"PortfolioVARChartGenerator?type=" + param
                + "\" BORDER=1 WIDTH=400 HEIGHT=300/>");
            out.println("</BODY>");
            out.println("</HTML>");
            out.flush();
            out.close();
        }
        catch (Exception e) {
            System.err.println(e.toString());
        }
        finally {
            out.close();
        }

    }

    
    private void outputFile(PrintWriter out, String file) {
	BufferedReader br = null;
	String line = null;
	File f_file = new File(file);
	
	if (! f_file.exists() || ! f_file.canRead()) {
	    try {
		out.println("document.write('Error processing file: "+f_file.getCanonicalPath()+": Cannot open and read file');");
	    } catch (Exception e) {
		out.println("document.write('Error processing file "+file+": "+e+"');");		
	    }
	} else {
	    try {
		br = new BufferedReader( new FileReader(f_file));
		while((line = br.readLine()) != null) {
		    out.println(line);
		}
	    } catch (Exception e) {
		out.println("document.write('Exception reading file "+file+": "+e+"');");
	    } finally {
		try {
		    br.close();
		} catch (Exception e) { // ignore
		}
	    }
	}
    }
}

