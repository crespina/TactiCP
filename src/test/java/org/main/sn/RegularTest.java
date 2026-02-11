/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.main.sn;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.main.sn.logic.GameStateReconstructionInstance;
import org.main.sn.logic.Possession;
import org.main.sn.logic.RegularInterval;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.CPFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class RegularTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest(CPSolver cp) {
        GameStateReconstructionInstance instance = new GameStateReconstructionInstance("data/SoccerNet/gamestate-2024/train/SNGS-060");
        //Possession poss = new Possession(cp, instance);
        //int[] possession = poss.getResult();
        int[] x = new int[]{0,0,0,0,1,1,0,0,0,0,2,2,2,2,0,0,0,0,0,3,3,3,3};
        int n = x.length;
        CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
        int[][] automaton = new int[][]{ // A+ 0* B+
                {-1, 1, 2, 3},
                {4, 1, -1, -1},
                {5, -1, 2, -1},
                {6, -1, -1, 3},
                {4, 1, 7, 8},
                {5, 9, 2, 10},
                {6, 11, 12, 3},
                {5, 1, -1, -1},
                {6, 1, -1, -1},
                {4, -1, 2, -1},
                {6, -1, 2, -1},
                {4, -1, -1, 3},
                {5, -1, -1, 3},
        };
        cp.post(new RegularInterval(x, itv, automaton, 0, Arrays.asList(7,8,9,10,11,12)));
        cp.fixPoint();
        assertEquals(4, itv.startMin());
        assertEquals(20, itv.endMax());
        itv.setEndMax(16);
        cp.fixPoint();
        assertEquals(4, itv.startMin());
        assertEquals(11, itv.endMax()); //the interval is end exclusive
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest2(CPSolver cp) {
        GameStateReconstructionInstance instance = new GameStateReconstructionInstance("data/SoccerNet/gamestate-2024/train/SNGS-060");
        //Possession poss = new Possession(cp, instance);
        //int[] possession = poss.getResult();
        int[] x = new int[]{0,0,0,0,0,1,0,0,0,0,2,2,2,2,0,0,0,0,0,3,3,3,3};
        int n = x.length;
        CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
        int[][] automaton = new int[][]{ // A 0* B
                {-1, 1, 2, 3},
                { 1,-1, 4, 4},
                {2, 4,-1, 4},
                {3, 4, 4,-1},
                {-1,-1,-1,-1}   // accept (no further symbols allowed)

        };
        cp.post(new RegularInterval(x, itv, automaton, 0, Arrays.asList(4)));
        cp.fixPoint();
        assertEquals(5, itv.startMin());
        assertEquals(20, itv.endMax());
        itv.setStartMin(8);
        cp.fixPoint();
        assertEquals(13, itv.startMin());
        assertEquals(20, itv.endMax()); //the interval is end exclusive
    }

}
