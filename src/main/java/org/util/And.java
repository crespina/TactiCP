package org.util;

import org.maxicp.cp.engine.core.CPBoolVar;

import static org.maxicp.cp.CPFactory.isOr;
import static org.maxicp.cp.CPFactory.not;

public class And {

    public And() {
        throw new UnsupportedOperationException();
    }

    // and constraint
    public static CPBoolVar and(CPBoolVar... vars) {
        CPBoolVar result = vars[0];
        for (int i = 1; i < vars.length; i++) {
            result = not(
                    isOr(
                            not(result),
                            not(vars[i])
                    )
            );
        }
        return result;
    }


}
