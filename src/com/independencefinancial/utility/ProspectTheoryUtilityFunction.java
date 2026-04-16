package com.bcfinancial.utility;

import java.io.Serializable;
import com.bcfinancial.persistenceManager.Persistable;

/**
 * Kahneman-Tversky prospect theory utility function.
 *
 * Gains  (r >= 0): u(r) =          r^alpha                  — concave, diminishing marginal utility
 * Losses (r <  0): u(r) = -lambda * |r|^beta                — convex disutility, loss aversion
 *
 * Typical empirical values (Kahneman & Tversky 1992):
 *   alpha = 0.88, beta = 0.88, lambda = 2.25
 */
public class ProspectTheoryUtilityFunction implements UtilityFunction, Serializable {
    private static final long serialVersionUID = 3136716433666186410L;

    private double alpha;   // curvature exponent for gains  (0 < alpha <= 1)
    private double beta;    // curvature exponent for losses (0 < beta  <= 1)
    private double lambda;  // loss aversion multiplier (> 1 = more loss averse)

    public ProspectTheoryUtilityFunction(double alpha, double beta, double lambda) {
        this.alpha  = alpha;
        this.beta   = beta;
        this.lambda = lambda;
    }

    /**
     * Called during deserialization.  Provides safe defaults if fields are missing
     * (e.g. objects serialized by an older version that used logBase/powerBase).
     */
    private Object readResolve() {
        if (alpha  == 0.0) alpha  = 0.88;
        if (beta   == 0.0) beta   = 0.88;
        if (lambda == 0.0) lambda = 2.25;
        return this;
    }

    public double getWeightedUtility(double[] sortedReturns) {
        // ignore the highest and lowest 1% of the returns as outliers
        int index1 = 1*sortedReturns.length/100;
        int index2 = 99*sortedReturns.length/100;

        double[] rets = new double[index2-index1];
        for (int i=index1;i<index2;i++) {
            rets[i-index1]=sortedReturns[i];
        }

        double[] utilities = getUtilities(rets);
        double weightedUtility = 0.0;
        for (int i=0;i<utilities.length;i++) {
            weightedUtility += utilities[i];
        }
        weightedUtility /= utilities.length;
        return weightedUtility;
    }

    public double[] getUtilities(double[] returns) {
        double [] utilities = new double[returns.length];
        for (int i=0;i<returns.length;i++) {
            double r = returns[i];
            if (r >= 0) {
                utilities[i] = Math.pow(r, alpha);
            } else {
                utilities[i] = -lambda * Math.pow(Math.abs(r), beta);
            }
        }
        return utilities;
    }

    public double getAlpha()  { return alpha;  }
    public double getBeta()   { return beta;   }
    public double getLambda() { return lambda; }

    public ProspectTheoryUtilityFunction deepCopy() {
        return new ProspectTheoryUtilityFunction(alpha, beta, lambda);
    }
}
