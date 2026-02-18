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
import org.main.util.Automaton;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.CPFactory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Objective;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.main.util.Automaton.complement_A_0STAR_B;
import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.setTimes;


public class RegularTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest(CPSolver cp) {
        GameStateReconstructionInstance instance = new GameStateReconstructionInstance("data/SoccerNet/gamestate-2024/train/SNGS-060");
        //Possession poss = new Possession(cp, instance);
        //int[] possession = poss.getResult();
        int[] x = new int[]{0,0,0,0,1,1,0,0,0,0,2,2,2,2,0,0,0,0,0,3,3,3,3};
        CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
        cp.post(new RegularInterval(x, itv, Automaton.APLUS_0STAR_BPLUS(3)));
        cp.fixPoint();
        assertEquals(4, itv.startMin());
        assertEquals(23, itv.endMax());
        itv.setEndMax(16);
        cp.fixPoint();
        assertEquals(4, itv.startMin());
        assertEquals(14, itv.endMax()); //the interval is end exclusive
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest2(CPSolver cp) {
        //GameStateReconstructionInstance instance = new GameStateReconstructionInstance("data/SoccerNet/gamestate-2024/train/SNGS-060");
        //Possession poss = new Possession(cp, instance);
        //int[] possession = poss.getResult();
        int[] x = new int[]{0,0,0,0,0,1,0,0,0,0,2,2,2,2,0,0,0,0,0,3,3,3,3};
        CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
        cp.post(new RegularInterval(x, itv, Automaton.A_0STAR_B(3)));
        cp.fixPoint();
        assertEquals(5, itv.startMin());
        assertEquals(20, itv.endMax());
        itv.setStartMin(8);
        cp.fixPoint();
        assertEquals(13, itv.startMin());
        assertEquals(20, itv.endMax()); //the interval is end exclusive
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest3(CPSolver cp) {

        //int[] x = new int[]{4, 1, 2, 3, 4, 5, 6, 5, 3, 2, 1, 5, 2, 3, 4, 6};
        int[] x = new int[]{0,0,0,0,0,1,0,0,0,0,2,2,2,2,0,0,0,0,0,3,3,3,3,0};
        int nPlayers = 3;
        Set<Integer> As = Set.of(1);
        Set<Integer> Bs = Set.of(2);

        Automaton complement = complement_A_0STAR_B(nPlayers, As, Bs);
        Automaton original = Automaton.A_0STAR_B(nPlayers, As, Bs);

        CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
        cp.post(new RegularInterval(x, itv, complement));
        cp.fixPoint();
        CPIntVar start = start(itv);
        CPIntVar end = end(itv);
        DFSearch search = makeDfs(cp, Searches.firstFail(start, end));

        search.onSolution(() -> {
            int realEnd = itv.endMin()-1;
            System.out.println("solution: " + itv.startMin() + " to " + realEnd );
        });
        search.solve();

    }

}
