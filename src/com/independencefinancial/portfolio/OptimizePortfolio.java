package com.bcfinancial.portfolio;

import com.bcfinancial.persistenceManager.*;
import com.bcfinancial.portfolio.*;
import com.bcfinancial.user.*;
import com.bcfinancial.util.communicator.*;
import java.util.*;

public class OptimizePortfolio {
    
    private int analysis_end_year;
    private int analysis_end_month; 
    private int analysis_end_date;
    private int analysis_duration; // how many years
    private int analysis_time_period; // how many days
    private String username = null;
    private String portfolioname = null;
    private int maxEpochs;
    
    public static void main(String[] args) {


	new OptimizePortfolio(args);
    }


    public OptimizePortfolio(String[] args) {
	
	if (args.length < 8) {
	    System.out.println("usage: OptimizePortfolio <end_year> <end_month> <end_date> <duration_years> <time_period_days> <user> <portfolio_name> <max_epochs>");
	    System.exit(1);
	}

	
	analysis_end_year = Integer.parseInt(args[0]);
	analysis_end_month = Integer.parseInt(args[1]);
	analysis_end_date = Integer.parseInt(args[2]);
	analysis_duration = Integer.parseInt(args[3]);
	analysis_time_period = Integer.parseInt(args[4]);
	username = args[5];
	portfolioname = args[6];
	maxEpochs = Integer.parseInt(args[7]);


	// set the end of the analysis period
	GregorianCalendar analysisEndDate = new GregorianCalendar(analysis_end_year,analysis_end_month,analysis_end_date);
	Date endDate = analysisEndDate.getTime(); // this converts it to a Date
	
	// set the beginning of the analysis period as the end minus the duration
	GregorianCalendar analysisStartDate = (GregorianCalendar)analysisEndDate.clone();
	analysisStartDate.add(GregorianCalendar.YEAR, -analysis_duration);
	Date startDate = analysisStartDate.getTime(); // this converts to a Date
	int timePeriodDays = analysis_time_period;
	
	System.out.println("optimizing portfolio");
	try {
	    
	    // Persistence Managers
	    PersistenceManagerImpl upm = new PersistenceManagerImpl(User.class);
	    PersistenceManagerImpl popm = new PersistenceManagerImpl(PortfolioOptimization.class);


	    System.out.println("Created Persistence Managers");

	    
	    // Create the table
	    try {
		popm.createTable("portfolio_optimizations");
		System.out.println("created table");
	    } catch (Exception e) {
		System.out.println("exception creating table: "+e);
	    }
	    
	    // Get the users
	    User user = (User)upm.select("users",username);
	    
	    // Optimize the portfolios and store

	    PortfolioOptimizationThread ot = new PortfolioOptimizationThread(user,portfolioname,startDate,endDate,timePeriodDays,maxEpochs);
	    
	    String ot_name = ot.getPOName();
	    
	    ot.start();
	    ot.join();
	    
	    
	    
	    try {
		Thread.sleep(5000);
	    } catch (InterruptedException ie) {
		// ignore
	    }
	    


	    // Retrieve the optimization
	    PortfolioOptimization po = (PortfolioOptimization) popm.select("portfolio_optimizations",ot_name);

	    System.out.println("po: "+po);
	    
	    
	    
	} catch (Exception e) {
	    System.out.println("Exception: "+e);
	    e.printStackTrace();
	}
	
    }
    
    
    
    private class PortfolioOptimizationThread extends Thread {
	
	User user;
	String portfolioName;
	String name;
	Date startDate;
	Date endDate;
	int timePeriodDays;
	int maxEpochs;
	
	
	public PortfolioOptimizationThread(User user, String portfolioName, Date startDate, Date endDate, int timePeriodDays, int maxEpochs) {
	    this.user = user;
	    this.portfolioName = portfolioName;
	    this.name = user.getUsername()+"|"+portfolioName+"|"+(new Date()).toString();
	    this.startDate = startDate;
	    this.endDate = endDate;
	    this.timePeriodDays = timePeriodDays;
	    this.maxEpochs = maxEpochs;
	    
	    
	}
	
	
	public String getPOName() {
	    return name;
	}
	
	
	public void run() {
	    
	    try {
		String commandProcessor = "com.bcfinancial.servlet.OptimizePortfolioMessageProcessor";
		Vector v = new Vector();
		v.add(user);
		v.add(portfolioName);
		v.add(startDate);
		v.add(endDate);
		v.add(timePeriodDays);
		v.add(maxEpochs);
		
		System.out.println("about to communicate to servlet");
		Object object = ServletCommunicator.communicate(commandProcessor,v);
		System.out.println("after communicate to servlet");
		if (object instanceof Exception) {
		    throw (Exception) object;
		} 

		// should be a PortfolioOptimization that came back in the state of "running"
		if (object instanceof PortfolioOptimization) {
		    System.out.println((PortfolioOptimization) object);
		} else {
		    System.out.println("Received an opbject back, but it's not a PortfolioOptimization");
		}
		
	    } catch (Exception e) {
		System.out.println("Exception starting thread: "+e);
	    }
	}
    }
}
