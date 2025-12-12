package org.main.util;

import org.maxicp.cp.engine.core.CPSolver;

public interface ConstraintPattern {

    void apply(CPSolver cp, Instance instance);

    String getName();
}
