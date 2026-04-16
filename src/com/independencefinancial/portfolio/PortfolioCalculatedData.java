package com.bcfinancial.portfolio;

import java.util.*;

import java.io.Serializable;
import java.util.logging.Logger;


public class PortfolioCalculatedData implements Serializable {
    private static final long serialVersionUID = 2L;
    private static final Logger logger =
        Logger.getLogger(PortfolioCalculatedData.class.getName());

    static boolean DEBUG = false;
    private PortfolioCalculationRequest portfolioCalculationRequest = null;
    private double[] sortedReturns = null;
    private double[] sortedUtilities = null;
    private double weightedUtility;
    private double maxDrawdown;
    private double fullPeriodCagr;
    private Exception exception = null;

    public PortfolioCalculatedData (PortfolioCalculationRequest portfolioCalculationRequest,double[] sortedReturns, double[] sortedUtilities,double weightedUtility, double maxDrawdown, double fullPeriodCagr) {
	this.portfolioCalculationRequest = portfolioCalculationRequest;
	this.sortedReturns = sortedReturns;
	this.sortedUtilities = sortedUtilities;
	this.weightedUtility = weightedUtility;
	this.maxDrawdown = maxDrawdown;
	this.fullPeriodCagr = fullPeriodCagr;
    }
    
    public PortfolioCalculationRequest getPortfolioCalculationRequest() {
	return portfolioCalculationRequest;
    }
    
    public double[] getSortedReturns() {
	return sortedReturns;
    }

    public double[] getSortedUtilities() {
	return sortedUtilities;
    }

    public double getWeightedUtility() {
	return weightedUtility;
    }

    public double getAverageCAGR() {
	double total = 0.0;
	for (int i=0;i<sortedReturns.length;i++)
	    total += sortedReturns[i];
	return total /= sortedReturns.length;
    }

    public double get95VAR() {
	int index = 5*sortedReturns.length/100;
	return sortedReturns[index];
    }

    public double get99VAR() {
	int index = 1*sortedReturns.length/100;
	return sortedReturns[index];
    }

    public double get95VTG() {
	int index = 95*sortedReturns.length/100;
	return sortedReturns[index];
    }
    public double get99VTG() {
	int index = 99*sortedReturns.length/100;
	return sortedReturns[index];
    }

    public double getMaxDrawdown() {
	return maxDrawdown;
    }

    public double getFullPeriodCagr() {
	return fullPeriodCagr;
    }

    /**
     * Annualised Sharpe ratio (4% risk-free rate).
     * Computed from de-annualized period returns so the variance is not inflated
     * by the high annualization exponent used on short rolling windows.
     */
    public double getSharpeRatio() {
	if (sortedReturns == null || sortedReturns.length < 2) return 0.0;
	int timePeriodDays = portfolioCalculationRequest.getTimePeriodDays();
	double periodYears = timePeriodDays / 365.0;
	double rfPeriod = Math.pow(1.04, periodYears) - 1.0;

	// De-annualize: convert each annualized CAGR back to its actual period return
	double sumP = 0.0;
	double[] pr = new double[sortedReturns.length];
	for (int i = 0; i < sortedReturns.length; i++) {
	    pr[i] = Math.pow(1.0 + sortedReturns[i], periodYears) - 1.0;
	    sumP += pr[i];
	}
	double mean = sumP / pr.length;

	double sumSq = 0.0;
	for (double r : pr) { double d = r - mean; sumSq += d * d; }
	double stdDev = Math.sqrt(sumSq / pr.length);
	if (stdDev == 0.0) return 0.0;

	// Annualise: multiply by sqrt(365 / timePeriodDays)
	return (mean - rfPeriod) / stdDev * Math.sqrt(365.0 / timePeriodDays);
    }

    public Exception getException() {
	return exception;
    }

    public void setException(Exception exception) {
	this.exception = exception;
    }

    private void debug(String s) {
	if (DEBUG)
	    System.out.println(s);
    }

    public PortfolioCalculatedData deepCopy() {
	PortfolioCalculationRequest pcr = portfolioCalculationRequest.deepCopy();
	double[] sr = new double[sortedReturns.length];
	System.arraycopy(sortedReturns,0,sr,0,sortedReturns.length);
	
	double[] su = new double[sortedUtilities.length];
	System.arraycopy(sortedUtilities,0,su,0,sortedUtilities.length);
	
	double wu = weightedUtility;
	double md = maxDrawdown;
	double fp = fullPeriodCagr;

	return new PortfolioCalculatedData (pcr,sr,su,wu,md,fp);

    }


    
}
