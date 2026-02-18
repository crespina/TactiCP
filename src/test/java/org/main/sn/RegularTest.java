/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.main.sn;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.main.sn.logic.RegularInterval;
import org.main.util.Automaton;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.CPFactory;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class RegularTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest(CPSolver cp) {
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

}
