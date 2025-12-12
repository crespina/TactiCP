package org.main.sn;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPSolver;

import java.io.IOException;

public class Main {

    public static void main(String[] args) throws IOException {
        // args[0] is the parameters file as a json dump
        // args[1] is the bounding boxes as a json dump

//            ParametersParser pp = new ParametersParser(args);
//            ConstraintPattern pattern = PatternRegistry.create(pp.patternName, pp.params);
//            TSVInstance instance = new TSVInstance(args);
//            CPSolver cp = CPFactory.makeSolver();
//            pattern.apply(cp, instance);

        TrackingInstance si = new TrackingInstance("data/SoccerNet/tracking/train/SNMOT-060");
        Pass pass = new Pass();
        CPSolver cp = CPFactory.makeSolver();
        pass.apply(cp, si);

//        GameStateReconstructionInstance gsrInstance = new GameStateReconstructionInstance("data/SoccerNet/gamestate-2024/train/SNGS-060");
//        CPSolver cp = CPFactory.makeSolver();
//        Pass pass = new Pass();
//        pass.apply(cp, gsrInstance);
    }
}
