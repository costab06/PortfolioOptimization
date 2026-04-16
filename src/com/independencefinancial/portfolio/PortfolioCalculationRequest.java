package com.bcfinancial.portfolio;

import java.util.*;
import com.bcfinancial.utility.*;

import java.io.Serializable;
import java.util.logging.Logger;

public class PortfolioCalculationRequest implements Serializable {
    private static final Logger logger =
        Logger.getLogger(PortfolioCalculationRequest.class.getName());
    
    private String portfolioName;
    private Portfolio portfolio;
    private Date startDate;
    private Date endDate;
    private int timePeriodDays;
    private UtilityFunction utilityFunction;

    public PortfolioCalculationRequest (String portfolioName, Portfolio portfolio,Date startDate,Date endDate, int timePeriodDays, UtilityFunction utilityFunction) {

	this.portfolioName = portfolioName;
	this.portfolio = portfolio;
	this.startDate = startDate;
	this.endDate = endDate;
	this.timePeriodDays = timePeriodDays;
	this.utilityFunction = utilityFunction;
    }

    public String getPortfolioName() {
	return portfolioName;
    }

    public void setPortfolioName(String name) {
	portfolioName = name;
    }

    public Portfolio getPortfolio() {
	return portfolio;
    }
    public Date getStartDate() {
	return startDate;
    }
    public Date getEndDate() {
	return endDate;
    }
    public int getTimePeriodDays() {
	return timePeriodDays;
    }
    public UtilityFunction getUtilityFunction() {
	return utilityFunction;
    }



    
    public String toString() {
	String ret = "portfolioName "+portfolioName+"\nstartDate "+startDate+"\nendDate "+endDate+"\ntimePeriodDays "+timePeriodDays+"\n"+portfolio.getPortfolioName();
	return ret;
    }
    
    
    public PortfolioCalculationRequest deepCopy() {
	PortfolioCalculationRequest p =  new PortfolioCalculationRequest(new String(portfolioName),
									 portfolio.deepCopy(),
									 (Date)startDate.clone(),
									 (Date)endDate.clone(),
									 timePeriodDays,
									 utilityFunction.deepCopy());
	
	
	return p;
    }
}
