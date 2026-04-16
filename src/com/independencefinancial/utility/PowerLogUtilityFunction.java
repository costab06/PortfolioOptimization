package com.bcfinancial.utility;

import java.io.Serializable;

/**
 * Backward-compatibility stub retained solely so that User objects serialized
 * before the rename to ProspectTheoryUtilityFunction can still be deserialized
 * from the database.  readResolve() transparently upgrades the instance to
 * ProspectTheoryUtilityFunction on load; subsequent saves will store the new class.
 *
 * @deprecated Use ProspectTheoryUtilityFunction directly.
 */
@Deprecated
public class PowerLogUtilityFunction extends ProspectTheoryUtilityFunction implements Serializable {
    private static final long serialVersionUID = 3136716433666186410L;

    public PowerLogUtilityFunction(double alpha, double beta, double lambda) {
        super(alpha, beta, lambda);
    }

    private Object readResolve() {
        return new ProspectTheoryUtilityFunction(getAlpha(), getBeta(), getLambda());
    }
}
