package org.main;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPSolver;

public class Main {

    public static void main(String[] args) {
        // args[0] is the parameters file as a json dump
        // args[1] is the bounding boxes as a json dump

            ParametersParser pp = new ParametersParser(args);
            ConstraintPattern pattern = PatternRegistry.create(pp.patternName, pp.params);
            TSVInstance instance = new TSVInstance(args);
            CPSolver cp = CPFactory.makeSolver();
            pattern.apply(cp, instance);

    }
}
