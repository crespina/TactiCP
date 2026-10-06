/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.main.sn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.main.sn.logic.SubstringRegular;
import org.main.util.Automaton;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.CPFactory;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


public class RegularTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest(CPSolver cp) {
        int[] x = new int[]{0,0,0,0,1,1,0,0,0,0,2,2,2,2,0,0,0,0,0,3,3,3,3};
        CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
        cp.post(new SubstringRegular(x, itv, Automaton.APLUS_0STAR_BPLUS(3)));
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
        cp.post(new SubstringRegular(x, itv, Automaton.A_0STAR_B(3)));
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
        cp.post(new SubstringRegular(x, itv, a));
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
        cp.post(new SubstringRegular(x, itv, a));
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
        cp.post(new SubstringRegular(padded_x, itv_1, a));
        cp.fixPoint();
        assertEquals(0, itv_1.startMin());
        assertEquals(7, itv_1.endMax());

        CPIntervalVar itv_2 = CPFactory.makeIntervalVar(cp);
        itv_2.setPresent();
        Automaton b = Automaton.NOTA_APLUS_NOTA(3, Set.of(2));
        cp.post(new SubstringRegular(padded_x, itv_2, b));
        cp.fixPoint();
        assertEquals(5, itv_2.startMin());
        assertEquals(11, itv_2.endMax());

        CPIntervalVar itv_3 = CPFactory.makeIntervalVar(cp);
        itv_3.setPresent();
        Automaton c = Automaton.NOTA_APLUS_NOTA(3, Set.of(3));
        cp.post(new SubstringRegular(padded_x, itv_3, c));
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

        Automaton a = Automaton.PAD_APLUS_NOTABSTAR_BPLUS_PAD(3, 1,3);
        cp.post(new SubstringRegular(padded_x, itv_1, a));
        cp.fixPoint();
        assertEquals(0, itv_1.startMin());
        assertEquals(14, itv_1.endMax());

    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest7(CPSolver cp) {
        int[] x = new int[]{1,1,1,1,1,1,1,1,1,1,0,1};

        CPIntervalVar itv_1 = CPFactory.makeIntervalVar(cp);
        itv_1.setPresent();

        Automaton a = Automaton.APLUS(3, Set.of(1));
        cp.post(new SubstringRegular(x, itv_1, a));
        cp.fixPoint();
        assertEquals(0, itv_1.startMin());
        assertEquals(12, itv_1.endMax());

        itv_1.setEndMax(11);
        cp.fixPoint();
        assertEquals(0, itv_1.startMin());
        assertEquals(10, itv_1.endMax());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void SimpleTest9(CPSolver cp) {
        int[] x = new int[]{0,1,1,1,1,0,0,0,2,2,2,0,0,0,1,1,1,0,0,0,2,2,2,3,3};
        int[] padded_x = Automaton.pad(x, 1);

        CPIntervalVar itv_1 = CPFactory.makeIntervalVar(cp);
        itv_1.setPresent();

        Automaton a = Automaton.NOTA_APLUS_NOTA(3, Set.of(0,2,3));
        cp.post(new SubstringRegular(padded_x, itv_1, a));
        itv_1.setStartMin(16);
        cp.fixPoint();
        assertEquals(17, itv_1.startMin());
        assertEquals(27, itv_1.endMax());


    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void NotxXplusNotxEndsAtHandover(CPSolver cp) {
        // 1 hands the ball straight to 2, 2 loses it, then 1 has it again
        int[] x = new int[]{0,1,1,2,2,2,0,1,1};
        int[] padded_x = Automaton.pad(x);

        CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
        itv.setPresent();
        cp.post(new SubstringRegular(padded_x, itv, Automaton.NOTX_XPLUS_NOTX(3, Set.of(1, 2))));

        // padded windows: a delimiter, one player's stay, a delimiter (2 delimits the first stay of 1)
        assertEquals(Set.of(List.of(1, 5), List.of(3, 8), List.of(7, 11)), windows(cp, itv));
    }

    @Test
    public void NotxXplusNotxAcceptsExactlyTheMaximalStays() {
        Random random = new Random(42);
        for (int trial = 0; trial < 300; trial++) {
            int nPlayers = 4;
            int[] x = random.ints(1 + random.nextInt(14), 0, nPlayers + 1).toArray();
            Set<Integer> As = new HashSet<>();
            for (int p = 1; p <= nPlayers; p++) if (random.nextBoolean()) As.add(p);

            // each maximal run x[s..e-1] of one member is the padded window [s, e + 2)
            Set<List<Integer>> expected = new HashSet<>();
            for (int s = 0, e = 1; s < x.length; s = e, e = s + 1) {
                while (e < x.length && x[e] == x[s]) e++;
                if (As.contains(x[s])) expected.add(List.of(s, e + 2));
            }

            CPSolver cp = CPFactory.makeSolver();
            CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
            itv.setPresent();
            SubstringRegular constraint = new SubstringRegular(Automaton.pad(x), itv, Automaton.NOTX_XPLUS_NOTX(nPlayers, As));
            String context = Arrays.toString(x) + " As=" + As;
            if (expected.isEmpty()) {
                assertThrows(InconsistencyException.class, () -> cp.post(constraint), context);
            } else {
                cp.post(constraint);
                assertEquals(expected, windows(cp, itv), context);
            }
        }
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void ExtendedPassAfterHandover(CPSolver cp) {
        // 2 hands the ball straight to 1, who then passes it back to 2
        int[] x = new int[]{0,2,2,1,1,0,0,2,2,0};
        CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
        itv.setPresent();
        cp.post(new SubstringRegular(Automaton.pad(x), itv, Automaton.PAD_APLUS_0PLUS_BPLUS_PAD(3, Set.of(1, 2), Set.of(1, 2))));

        // padded window: the delimiter 2, the pass 1 1 0 0 2 2, the delimiter 0
        assertEquals(Set.of(List.of(3, 11)), windows(cp, itv));
    }

    @Test
    public void ExtendedPassAcceptsExactlyTheMaximalPasses() {
        Random random = new Random(7);
        for (int trial = 0; trial < 300; trial++) {
            int nPlayers = 4;
            int[] x = random.ints(1 + random.nextInt(14), 0, nPlayers + 1).toArray();
            Set<Integer> As = new HashSet<>(), Bs = new HashSet<>();
            for (int p = 1; p <= nPlayers; p++) {
                if (random.nextBoolean()) As.add(p);
                if (random.nextBoolean()) Bs.add(p);
            }

            // a pass is three consecutive maximal runs: a in As, nobody, b in Bs with b != a;
            // runs x[s..e-1] of a and x[s'..e'-1] of b give the padded window [s, e' + 2)
            List<int[]> runs = new ArrayList<>();
            for (int s = 0, e = 1; s < x.length; s = e, e = s + 1) {
                while (e < x.length && x[e] == x[s]) e++;
                runs.add(new int[]{x[s], s, e});
            }
            Set<List<Integer>> expected = new HashSet<>();
            for (int i = 0; i + 2 < runs.size(); i++) {
                int a = runs.get(i)[0], b = runs.get(i + 2)[0];
                if (As.contains(a) && runs.get(i + 1)[0] == 0 && Bs.contains(b) && b != a) {
                    expected.add(List.of(runs.get(i)[1], runs.get(i + 2)[2] + 2));
                }
            }

            CPSolver cp = CPFactory.makeSolver();
            CPIntervalVar itv = CPFactory.makeIntervalVar(cp);
            itv.setPresent();
            SubstringRegular constraint = new SubstringRegular(Automaton.pad(x), itv, Automaton.PAD_APLUS_0PLUS_BPLUS_PAD(nPlayers, As, Bs));
            String context = Arrays.toString(x) + " As=" + As + " Bs=" + Bs;
            if (expected.isEmpty()) {
                assertThrows(InconsistencyException.class, () -> cp.post(constraint), context);
            } else {
                cp.post(constraint);
                assertEquals(expected, windows(cp, itv), context);
            }
        }
    }

    /** Every [start, end) the posted constraints accept for {@code itv}. */
    private static Set<List<Integer>> windows(CPSolver cp, CPIntervalVar itv) {
        CPIntVar start = CPFactory.start(itv);
        CPIntVar end = CPFactory.end(itv);
        Set<List<Integer>> windows = new HashSet<>();
        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFailBinary(start, end));
        search.onSolution(() -> windows.add(List.of(start.min(), end.min())));
        search.solve();
        return windows;
    }

}
