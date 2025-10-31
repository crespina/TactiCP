package org.main;

import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;

public interface ConstraintPattern {

    void apply(CPSolver cp, Instance instance);
    String getName();
}
