package com.bcfinancial.utility;

import java.io.Serializable;

/**
 * Conditional Value at Risk (CVaR / Expected Shortfall) utility function.
 *
 * Returns the mean of the worst {@code tailFraction} of the return distribution.
 * The result is negative when the portfolio has bad tail behaviour and less
 * negative (or positive) as tail losses shrink, so the optimiser naturally
 * maximises toward less-negative values.
 *
 * Default tail fraction: 5% (i.e. mean of the worst 5% of returns).
 *
 * Per-period utility (getUtilities): returns the raw returns unchanged so
 * that the distribution can be charted as-is.
 */
public class CVaRUtilityFunction implements UtilityFunction, Serializable {
    private static final long serialVersionUID = 1L;

    private double tailFraction;   // fraction of worst returns to average (default 5%)

    public CVaRUtilityFunction() { this.tailFraction = 0.05; }
    public CVaRUtilityFunction(double tailFraction) { this.tailFraction = tailFraction; }

    /** Provides safe defaults when deserialising older serialised instances. */
    private Object readResolve() {
        if (tailFraction == 0.0) tailFraction = 0.05;
        return this;
    }

    /**
     * Returns the mean of the worst {@code tailFraction} of the distribution.
     * Input must be sorted ascending (smallest first) — guaranteed by
     * PortfolioCalculator.getSortedReturnDistribution().
     */
    @Override
    public double getWeightedUtility(double[] sortedReturns) {
        if (sortedReturns.length == 0) return 0.0;
        int cutoff = Math.max(1, (int)(tailFraction * sortedReturns.length));
        double sum = 0.0;
        for (int i = 0; i < cutoff; i++) sum += sortedReturns[i];
        return sum / cutoff;
    }

    /** Pass-through: CVaR is a summary statistic, not a per-point transform. */
    @Override
    public double[] getUtilities(double[] returns) {
        return returns.clone();
    }

    public double getTailFraction() { return tailFraction; }

    @Override
    public CVaRUtilityFunction deepCopy() {
        return new CVaRUtilityFunction(tailFraction);
    }
}
