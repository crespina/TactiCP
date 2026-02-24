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

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest3(CPSolver cp) {
        int[] x = new int[]{0,0,1,1,1,1,2,2,2,3,3,3};
        CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
        itv.setPresent();
        Automaton a = Automaton.A_NOTBSTAR_B(3, 1, 3);
        cp.post(new RegularInterval(x, itv, a));
        cp.fixPoint();
        assertEquals(5, itv.startMin());
        assertEquals(10, itv.endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest4(CPSolver cp) {
        int[] x = new int[]{0, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 10, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 14, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 13, 15, 15, 15, 15, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 25, 25, 25, 25, 25, 0, 0, 0, 0, 0, 0, 0, 0, 0, 25, 25, 25, 25, 25, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 24, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 17, 17, 17, 17, 17, 17, 0, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 12, 12, 12, 12, 12, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 11, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 24, 24, 24, 17, 17, 17, 17, 17, 17, 0};
        CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
        itv.setPresent();
        Automaton a = Automaton.A_0STAR_B(25, Set.of(11), Set.of(1,2,3,4,5,6,7,8,9,10,12,13,14,15,16,17,18,19,20,21,22,23,24));
        cp.post(new RegularInterval(x, itv, a));
        cp.fixPoint();
        assertEquals(84, itv.startMin());
        assertEquals(742, itv.endMax());

        itv.setEndMax(698);
        cp.fixPoint();
        assertEquals(84, itv.startMin());
        assertEquals(675, itv.endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest5(CPSolver cp) {
        int[] x = new int[]{1,1,1,1,1,2,2,2,2,3,3,3};
        int[] padded_x = Automaton.pad(x);

        CPIntervalVar itv_1 = CPFactory.makeIntervalVar(cp);
        itv_1.setPresent();

        Automaton a = Automaton.NOTA_APLUS_NOTA(3, Set.of(1));
        cp.post(new RegularInterval(padded_x, itv_1, a));
        cp.fixPoint();
        assertEquals(0, itv_1.startMin());
        assertEquals(7, itv_1.endMax());

        CPIntervalVar itv_2 = CPFactory.makeIntervalVar(cp);
        itv_2.setPresent();
        Automaton b = Automaton.NOTA_APLUS_NOTA(3, Set.of(2));
        cp.post(new RegularInterval(padded_x, itv_2, b));
        cp.fixPoint();
        assertEquals(5, itv_2.startMin());
        assertEquals(11, itv_2.endMax());

        CPIntervalVar itv_3 = CPFactory.makeIntervalVar(cp);
        itv_3.setPresent();
        Automaton c = Automaton.NOTA_APLUS_NOTA(3, Set.of(3));
        cp.post(new RegularInterval(padded_x, itv_3, c));
        cp.fixPoint();
        assertEquals(9, itv_3.startMin());
        assertEquals(14, itv_3.endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest6(CPSolver cp) {
        int[] x = new int[]{1,1,1,1,1,2,2,2,2,3,3,3,2,2,2,2,2};
        int[] padded_x = Automaton.pad(x);

        CPIntervalVar itv_1 = CPFactory.makeIntervalVar(cp);
        itv_1.setPresent();

        Automaton a = Automaton.PAD_APLUS_PADSTAR_BPLUS_PAD(3, 1,3);
        cp.post(new RegularInterval(padded_x, itv_1, a));
        cp.fixPoint();
        assertEquals(0, itv_1.startMin());
        assertEquals(14, itv_1.endMax());

    }




}
