package org.main;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPSolver;

public class Main {

    public static void main(String[] args) {
        // args[0] is path to the parameters file
        // args[1] is the path to the bounding boxes txt file

        //For debug purposes
        args = new String[2];
        args[0] = "data/parameters.json";
        args[1] = "data/bbox/alarm_sid_40.0_time_20251029_144753.txt";

        ParametersParser pp = new ParametersParser(args);
        ConstraintPattern pattern = PatternRegistry.create(pp.patternName, pp.params);
        Instance instance = new Instance(args[1]);
        CPSolver cp = CPFactory.makeSolver();
        pattern.apply(cp, instance);
    }
}
