package com.bcfinancial.utility;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Omega ratio utility function.
 *
 * The Omega ratio at threshold L is the ratio of probability-weighted gains
 * above L to probability-weighted losses below L:
 *
 *   Omega(L) = sum(max(r - L, 0)) / sum(max(L - r, 0))
 *
 * A value > 1 means more probability-weighted return above the threshold than
 * below.  Values are always positive; the optimiser maximises naturally.
 *
 * If no return falls below the threshold the denominator is zero; a capped
 * value of 100.0 is returned to avoid infinity propagating into comparisons.
 *
 * Outer 1%/99% tail trimming is applied to remove data errors and extreme outliers.
 *
 * Per-period utility (getUtilities): r - rfAnnual (excess return per period).
 */
public class OmegaRatioUtilityFunction implements UtilityFunction, Serializable {
    private static final long serialVersionUID = 1L;

    private double rfAnnual;   // annualised threshold / risk-free rate (default 4%)

    public OmegaRatioUtilityFunction() { this.rfAnnual = 0.04; }
    public OmegaRatioUtilityFunction(double rfAnnual) { this.rfAnnual = rfAnnual; }

    /** Provides safe defaults when deserialising older serialised instances. */
    private Object readResolve() {
        if (rfAnnual == 0.0) rfAnnual = 0.04;
        return this;
    }

    @Override
    public double getWeightedUtility(double[] sortedReturns) {
        int index1 = 1 * sortedReturns.length / 100;
        int index2 = 99 * sortedReturns.length / 100;
        double[] trimmed = Arrays.copyOfRange(sortedReturns, index1, index2);
        if (trimmed.length == 0) return 0.0;

        double gains = 0.0, losses = 0.0;
        for (double r : trimmed) {
            if (r > rfAnnual) gains  += (r - rfAnnual);
            else              losses += (rfAnnual - r);
        }
        if (losses == 0.0) return 100.0;   // all returns above threshold — cap to avoid infinity
        return gains / losses;
    }

    @Override
    public double[] getUtilities(double[] returns) {
        double[] utilities = new double[returns.length];
        for (int i = 0; i < returns.length; i++) {
            utilities[i] = returns[i] - rfAnnual;
        }
        return utilities;
    }

    public double getRfAnnual() { return rfAnnual; }

    @Override
    public OmegaRatioUtilityFunction deepCopy() {
        return new OmegaRatioUtilityFunction(rfAnnual);
    }
}
