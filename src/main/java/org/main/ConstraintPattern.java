package org.main;

import org.maxicp.cp.engine.core.CPSolver;

public interface ConstraintPattern {

    void apply(CPSolver cp, Instance instance);

    String getName();
}
