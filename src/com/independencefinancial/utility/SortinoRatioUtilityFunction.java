package com.bcfinancial.utility;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Sortino ratio utility function.
 *
 * Like the Sharpe ratio, but only penalises downside volatility (returns below
 * the risk-free threshold), leaving upside variance unpunished.
 *
 *   Sortino = (mean - rf) / downsideDeviation
 *
 * where downsideDeviation = sqrt( sum((rf - r)^2 for r < rf) / N )
 * and N is the total sample count (not just the downside count).
 *
 * Per-period utility (getUtilities):
 *   r >= rf  →  r - rf              (excess return, positive)
 *   r <  rf  →  -(rf - r)^2         (squared shortfall, negative)
 */
public class SortinoRatioUtilityFunction implements UtilityFunction, Serializable {
    private static final long serialVersionUID = 1L;

    private double rfAnnual;   // annualised risk-free rate (default 4%)

    public SortinoRatioUtilityFunction() { this.rfAnnual = 0.04; }
    public SortinoRatioUtilityFunction(double rfAnnual) { this.rfAnnual = rfAnnual; }

    /** Provides safe defaults when deserialising older serialised instances. */
    private Object readResolve() {
        if (rfAnnual == 0.0) rfAnnual = 0.04;
        return this;
    }

    @Override
    public double getWeightedUtility(double[] sortedReturns) {
        // Trim the outer 1% tails as outliers
        int index1 = 1 * sortedReturns.length / 100;
        int index2 = 99 * sortedReturns.length / 100;
        double[] trimmed = Arrays.copyOfRange(sortedReturns, index1, index2);
        if (trimmed.length == 0) return 0.0;

        double mean = 0.0;
        for (double r : trimmed) mean += r;
        mean /= trimmed.length;

        // Downside deviation: divide by total N (standard Sortino formula)
        double downsideSumSq = 0.0;
        for (double r : trimmed) {
            if (r < rfAnnual) {
                double shortfall = rfAnnual - r;
                downsideSumSq += shortfall * shortfall;
            }
        }
        double downsideDev = Math.sqrt(downsideSumSq / trimmed.length);

        if (downsideDev == 0.0) {
            // No downside returns — return a large positive value proportional to excess return
            return mean > rfAnnual ? 10.0 : 0.0;
        }
        return (mean - rfAnnual) / downsideDev;
    }

    @Override
    public double[] getUtilities(double[] returns) {
        double[] utilities = new double[returns.length];
        for (int i = 0; i < returns.length; i++) {
            double r = returns[i];
            if (r >= rfAnnual) {
                utilities[i] = r - rfAnnual;
            } else {
                double shortfall = rfAnnual - r;
                utilities[i] = -(shortfall * shortfall);
            }
        }
        return utilities;
    }

    public double getRfAnnual() { return rfAnnual; }

    @Override
    public SortinoRatioUtilityFunction deepCopy() {
        return new SortinoRatioUtilityFunction(rfAnnual);
    }
}
