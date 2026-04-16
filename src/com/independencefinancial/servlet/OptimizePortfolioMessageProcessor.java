package com.bcfinancial.servlet;

import java.io.*;
import java.util.*;

import com.bcfinancial.executor.PortfolioParallelExecutor;
import com.bcfinancial.util.email.*;
import com.bcfinancial.persistenceManager.*;
import com.bcfinancial.portfolio.*;
import com.bcfinancial.user.*;
import com.bcfinancial.utility.*;


public class OptimizePortfolioMessageProcessor implements CommandProcessor {

    final static boolean DEBUG=true;
    PersistenceManagerImpl portfolioPersistenceManager = null;
    PersistenceManagerImpl portfolioOptimizationPersistenceManager = null;

    // Replaces the ComputeFarm JobRunner / JobRunnerFactory pattern.
    // One shared executor per processor instance – thread-safe.
    private final PortfolioParallelExecutor parallelExecutor = new PortfolioParallelExecutor();

    public void init() throws Exception {
	try {
	    portfolioPersistenceManager = new PersistenceManagerImpl(Portfolio.class);
	    portfolioOptimizationPersistenceManager = new PersistenceManagerImpl(PortfolioOptimization.class);
	} catch (Exception e) {

	    System.out.println("Exception creating persistence manager for Portfolio, and PortfolioOptimizer classes: "+e);
	    throw e;
	}
    }
    
    public void process(ObjectInputStream in, ObjectOutputStream out) throws Exception {

	Portfolio portfolio = null;
	UtilityFunction utilityFunction = null;
	PortfolioCalculatedData sp500CalculatedData=null, originalPortfolioCalculatedData=null;
	ArrayList portfolioCalculatedDataList = new ArrayList(); // returned from the epoch
	PortfolioCalculatedData[] portfolioCalculatedDataArray=null;
	Portfolio[] portfolioArray=null;
	double[][] returnsArray=null;
	
	
	// read the serialized user and portfolio data from applet
	User user = (User) in.readObject();
	String portfolioName = (String) in.readObject();
	Date startDate = (Date) in.readObject();
	Date endDate = (Date) in.readObject();
	Integer timePeriodDays = (Integer) in.readObject();
	Integer maxEpochs = (Integer) in.readObject();


	String optimizationName=null;
	String state=null;
	PortfolioOptimization portfolioOptimization=null;

	in.close();
	
	if (DEBUG) {
	    System.out.println(this.getClass().getName());
	    System.out.println("user: "+user);
	    System.out.println("portfolioName: "+portfolioName);
	    System.out.println("startDate: "+startDate);
	    System.out.println("endDate: "+endDate);
	    System.out.println("timePeriodDays: "+timePeriodDays);
	    System.out.println("maxEpochs: "+maxEpochs);
	    
	}
	
	
	try {
	    // get the portfolio from the system
	    String pn = user.getUsername()+"|"+portfolioName;
	    portfolio = (Portfolio) portfolioPersistenceManager.select("portfolios",pn);

	    if (portfolio == null) {
		System.out.println("Cannot retrieve portfolio: "+pn);
		return;
	    }


	    System.out.println("Got the portfolio");
	    
	    // get the utility function
	    utilityFunction = user.getUtilityFunction();
	    System.out.println("Got the utility function");	    
	    
	    // the name is the portfolio name with the date appended
	    // update the list of portfolio optimizations in the portfolio as well
	    optimizationName = user.getUsername()+"|"+portfolioName+"|"+(new Date()).toString();
	    
	    state = "Running: "+(new Date()).toString();
	    
	    
	    // add the optimization to the list of optimizations for this portfolio
	    portfolio.addOptimization(optimizationName);
	    // persist the portfolio
	    portfolioPersistenceManager.update("portfolios",user.getUsername()+"|"+portfolioName,portfolio);
	    
	    
	    // create the optimization and set the state to running
	    portfolioOptimization = new PortfolioOptimization(optimizationName,state,sp500CalculatedData,originalPortfolioCalculatedData,portfolioCalculatedDataArray, portfolioArray, returnsArray);
	    
	    // persist the optimization
	    portfolioOptimizationPersistenceManager.insert("portfolio_optimizations",optimizationName,portfolioOptimization);
	    
	    // send the optimization back to the applet
	    out.writeObject(portfolioOptimization);
	} catch (Exception e) {
	    System.out.println("Exception: problem storing portfolio optimization: "+e.toString());
	    e.printStackTrace();
	    out.writeObject(e);
	} finally {
	    out.flush();
	    out.close();
	    System.out.println("Data transmission complete.");
	}
	
	
	try {
	    
	    
	    // start churning the optimization
	    
	    
	    // get the SP500 data
	    sp500CalculatedData = getSP500Instance(startDate,endDate,timePeriodDays,utilityFunction);
	    
	    System.out.println("Got the sp500 data");
	    
	    
	    // get the original portfolio data
	    originalPortfolioCalculatedData = getOriginalInstance(portfolio,startDate,endDate,timePeriodDays,utilityFunction);
	    
	    System.out.println("Got the original portfolio data");
	    
	    
	    
	    double lastWeightedUtility = originalPortfolioCalculatedData.getWeightedUtility();
	    
	    
	    boolean stop = false;
	    boolean exception = false;
	    java.util.List list = null;
	    // get the variations on the original portfolio data 
	    for (int i=0;i<maxEpochs && !stop && !exception ;i++) { // epochs
		
		try {
		    list = getPortfolioInstances(portfolio,startDate,endDate,timePeriodDays,utilityFunction);
		} catch (Exception e) {
		    System.out.println("Exception computing the instances: "+e);
		    e.printStackTrace();
		    exception = true;
		    continue;
		}
		
		
		// Choose the one with the largest increase in utility and reset the weights
		PortfolioCalculatedData pcd = (PortfolioCalculatedData) Collections.max(list, new UtilityComparator());
		
		// if the weighted utility has decreased, then stop the loop before adding this to the accumulated data

		if (pcd.getWeightedUtility() < lastWeightedUtility) {
		    stop = true;
		    continue;
		}
		
		
		lastWeightedUtility = pcd.getWeightedUtility();
		
		if (DEBUG) {
		    String t = pcd.getPortfolioCalculationRequest().getPortfolioName();
		    String[] s = pcd.getPortfolioCalculationRequest().getPortfolio().getSymbols();
		    Double[] w = pcd.getPortfolioCalculationRequest().getPortfolio().getWeights();
		    double wu = pcd.getWeightedUtility();
		    double c = pcd.getAverageCAGR();
		    double v = pcd.get99VAR();
		    System.out.println("\n\nAfter Epoc: "+i+" choosing portfolio "+t+" with utility "+wu+", CAGR "+c+", and 99%VAR "+v);
		    for (int j=0;j<s.length;j++) {
			System.out.println(s[j]+" "+w[j]);
		    }
		}
		
		// change the name to indicate the epoch
		pcd.getPortfolioCalculationRequest().setPortfolioName("Epoch "+i);
		
		// add it to the list of thigs to return
		portfolioCalculatedDataList.add(pcd);
		
		// set the weights based on this one and loop
		portfolio = pcd.getPortfolioCalculationRequest().getPortfolio();
	    }
	    
	    
	    
	    // make arrays of the portfolio into for display
	    portfolioCalculatedDataArray = (PortfolioCalculatedData[])portfolioCalculatedDataList.toArray(new PortfolioCalculatedData[0]);
	    portfolioArray = new Portfolio[2+portfolioCalculatedDataList.size()];
	    returnsArray = new double[2+portfolioCalculatedDataList.size()][];
	    portfolioArray[0]=sp500CalculatedData.getPortfolioCalculationRequest().getPortfolio();
	    portfolioArray[1]=originalPortfolioCalculatedData.getPortfolioCalculationRequest().getPortfolio();
	    returnsArray[0]=sp500CalculatedData.getSortedReturns();
	    returnsArray[1]=originalPortfolioCalculatedData.getSortedReturns();
	    for (int i=0;i<portfolioCalculatedDataList.size();i++) {
		portfolioArray[i+2]=((PortfolioCalculatedData)portfolioCalculatedDataList.get(i)).getPortfolioCalculationRequest().getPortfolio();
		returnsArray[i+2]=((PortfolioCalculatedData)portfolioCalculatedDataList.get(i)).getSortedReturns();
	    }

	    
	    // Okay, all done.
	    

	    // create a new portfolio optimization with all the data
	    state = "Finished, "+(new Date()).toString();
	    portfolioOptimization = new PortfolioOptimization(optimizationName,state, sp500CalculatedData,originalPortfolioCalculatedData,portfolioCalculatedDataArray, portfolioArray, returnsArray);
	    
	    // update the optimization that was stored at the start
	    portfolioOptimizationPersistenceManager.update("portfolio_optimizations",optimizationName,portfolioOptimization);
	    
	    
	    // email the user that the optimization is complete
	    try {
		System.out.println("emailing user that optimization is finished");
		String subject = "Portfolio Optimization has completed";
		String body = optimizationName+" has completed: \n"+state;
		EmailSender.postMail(user.getUsername(),"BCFinancial",subject,body);
		
	    } catch(Exception e) {
		System.out.println("Exception sending email: "+e);
	    }
	    
	} catch (Exception e) {
	    System.out.println("Exception: calculating portfolio optimization: "+e.toString());
	    e.printStackTrace();
	}
    }
    
    // -------------------------------------------------------------------
    // Calculation helpers – now delegating to PortfolioParallelExecutor
    // instead of ComputeFarm Job/JobRunner.
    // -------------------------------------------------------------------

    private PortfolioCalculatedData getSP500Instance(Date startDate, Date endDate,
            int timePeriodDays, UtilityFunction utilityFunction) {
        String[] sp500 = new String[]{"SPY"};
        Double[] oneHundred = new Double[]{1.0};
        Portfolio sp500Portfolio = new Portfolio("SP500", sp500, oneHundred);
        return parallelExecutor.calculate("100% SP500 Portfolio", sp500Portfolio,
                startDate, endDate, timePeriodDays, utilityFunction);
    }

    private PortfolioCalculatedData getOriginalInstance(Portfolio portfolio,
            Date startDate, Date endDate, int timePeriodDays, UtilityFunction utilityFunction) {
        return parallelExecutor.calculate("Original Portfolio", portfolio,
                startDate, endDate, timePeriodDays, utilityFunction);
    }

    private java.util.List getPortfolioInstances(Portfolio portfolio, Date startDate,
            Date endDate, int timePeriodDays, UtilityFunction utilityFunction) throws Exception {

        java.util.List list = parallelExecutor.calculateVariants(
                portfolio, startDate, endDate, timePeriodDays, utilityFunction, 1.0);

        // Propagate any per-task exception so the epoch loop can react.
        for (Object o : list) {
            PortfolioCalculatedData ret = (PortfolioCalculatedData) o;
            if (ret.getException() != null) {
                throw ret.getException();
            }
        }
        return list;
    }
    


    private class UtilityComparator implements Comparator {
	public int compare (Object o1, Object o2) {
	    PortfolioCalculatedData p1 = (PortfolioCalculatedData) o1;
	    PortfolioCalculatedData p2 = (PortfolioCalculatedData) o2;
	    if (p1.getWeightedUtility() > p2.getWeightedUtility())
		return 1;
	    else if (p1.getWeightedUtility() < p2.getWeightedUtility())
		return -1;
	    else
		return 0;
	}
    }
    
    
    // This class sorts two numeric objects.
    class NumComparator implements Comparator {
	public int compare( Object object1, Object object2 ) {
	    return ((Double) object1).compareTo( (Double) object2 );
	}
	
    }







}
