package com.bcfinancial.utility;

import java.io.Serializable;
import java.util.Arrays;

/**
 * Asymmetric power utility function with a convex (increasing-disutility) loss curve.
 *
 * <p>Gains  (r &ge; 0): u(r) = r<sup>&alpha;</sup>
 * <br>Identical to Prospect Theory — concave, diminishing marginal utility.
 *
 * <p>Losses (r &lt; 0): u(r) = &minus;&lambda; &middot; |r|<sup>&beta;</sup>, &beta; &gt; 1
 * <br>Convex in |r|: each additional unit of loss hurts <em>more</em> than the previous.
 * Small losses feel tolerable; large losses feel disproportionately painful.
 * This is the opposite of Prospect Theory's loss side (&beta; &lt; 1 = diminishing disutility).
 *
 * <p><b>Default parameterisation:</b>
 * <ul>
 *   <li>&alpha; = 0.88  (Kahneman-Tversky gain exponent)
 *   <li>&beta;  = 1/&alpha; &asymp; 1.136  — geometric mirror of &alpha;: the two curves have
 *       symmetric curvatures and both pass through (0, 0) and (1, 1) before scaling.
 *   <li>&lambda; = 2.25  (Kahneman-Tversky loss aversion) — makes the loss side 2.25&times; steeper.
 * </ul>
 */
public class ConvexLossUtilityFunction implements UtilityFunction, Serializable {
    private static final long serialVersionUID = 1L;

    private double alpha;   // gain exponent (0 < alpha <= 1)
    private double beta;    // loss exponent (beta > 1 for increasing marginal disutility)
    private double lambda;  // loss aversion multiplier (> 1)

    /** Default constructor: α=0.88, β=1/0.88≈1.136, λ=2.25. */
    public ConvexLossUtilityFunction() {
        this.alpha  = 0.88;
        this.beta   = 1.0 / 0.88;   // ≈ 1.136
        this.lambda = 2.25;
    }

    public ConvexLossUtilityFunction(double alpha, double beta, double lambda) {
        this.alpha  = alpha;
        this.beta   = beta;
        this.lambda = lambda;
    }

    /** Provides safe defaults when deserialising older serialised instances. */
    private Object readResolve() {
        if (alpha  == 0.0) alpha  = 0.88;
        if (beta   == 0.0) beta   = 1.0 / 0.88;
        if (lambda == 0.0) lambda = 2.25;
        return this;
    }

    @Override
    public double getWeightedUtility(double[] sortedReturns) {
        // Trim the outer 1% tails as outliers
        int index1 = 1 * sortedReturns.length / 100;
        int index2 = 99 * sortedReturns.length / 100;
        double[] trimmed = Arrays.copyOfRange(sortedReturns, index1, index2);
        if (trimmed.length == 0) return 0.0;

        double[] utilities = getUtilities(trimmed);
        double sum = 0.0;
        for (double u : utilities) sum += u;
        return sum / utilities.length;
    }

    @Override
    public double[] getUtilities(double[] returns) {
        double[] utilities = new double[returns.length];
        for (int i = 0; i < returns.length; i++) {
            double r = returns[i];
            if (r >= 0) {
                utilities[i] = Math.pow(r, alpha);
            } else {
                // beta > 1 → convex in |r| → increasing marginal disutility
                utilities[i] = -lambda * Math.pow(Math.abs(r), beta);
            }
        }
        return utilities;
    }

    public double getAlpha()  { return alpha;  }
    public double getBeta()   { return beta;   }
    public double getLambda() { return lambda; }

    @Override
    public ConvexLossUtilityFunction deepCopy() {
        return new ConvexLossUtilityFunction(alpha, beta, lambda);
    }
}
