package org.main.sn.logic;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPSolver;

import java.io.IOException;
import java.util.Arrays;

public class Main {

    public static void main(String[] args) throws IOException {
        // args[0] is the parameters file as a json dump
        // args[1] is the bounding boxes as a json dump

//            ParametersParser pp = new ParametersParser(args);
//            ConstraintPattern pattern = PatternRegistry.create(pp.patternName, pp.params);
//            TSVInstance instance = new TSVInstance(args);
//            CPSolver cp = CPFactory.makeSolver();
//            pattern.apply(cp, instance);

//        TrackingInstance si = new TrackingInstance("data/SoccerNet/tracking/train/SNMOT-060");
//        CPSolver cp = CPFactory.makeSolver();
//        Possession possession = new Possession(cp, si);
//        System.out.println(Arrays.toString(possession.result));
//        Pass pass = new Pass(cp, si);


        GameStateReconstructionInstance gsrInstance = new GameStateReconstructionInstance("data/SoccerNet/gamestate-2024/train/SNGS-060");
        CPSolver cp = CPFactory.makeSolver();
        Possession possess = new Possession(cp, gsrInstance);
        System.out.println(Arrays.toString(possess.result));
        Pass passes = new Pass(cp, gsrInstance);
    }
}
