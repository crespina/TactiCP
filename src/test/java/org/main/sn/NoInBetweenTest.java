/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.main.sn;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.main.sn.logic.NoInBetween;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.CPFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class NoInBetweenTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest0(CPSolver cp) {
        CPIntVar start = CPFactory.makeIntVar(cp, 0, 10);
        CPIntVar end = CPFactory.makeIntVar(cp, 0, 10);
        int[] array = {1,1,-1,-1,-1,-1,-1,-1,2,2};
        int value = -1;

        cp.post(new NoInBetween(array, start, end, value));

        assertEquals(1, start.min());
        assertEquals(8, end.max());

        start.remove(0);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(8, end.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest1(CPSolver cp) {
        CPIntVar start = CPFactory.makeIntVar(cp, 0, 10);
        CPIntVar end = CPFactory.makeIntVar(cp, 0, 10);
        int[] array = {1,1,-1,-1,3,-1,-1,-1,2,2};
        int value = -1;

        cp.post(new NoInBetween(array, start, end, value));

        assertEquals(1, start.min());
        assertEquals(8, end.max());

        start.remove(0);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(8, end.max());

        start.remove(1);
        cp.fixPoint();

        assertEquals(4, start.min());
        assertEquals(4, start.max());
        assertEquals(8, end.min());
        assertEquals(8, end.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest2(CPSolver cp) {
        CPIntVar start = CPFactory.makeIntVar(cp, 0, 10);
        CPIntVar end = CPFactory.makeIntVar(cp, 0, 10);
        int[] array = {1,1,-1,-1,3,-1,-1,-1,2,2};
        int value = -1;

        cp.post(new NoInBetween(array, start, end, value));

        assertEquals(1, start.min());
        assertEquals(8, end.max());

        start.remove(0);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(8, end.max());

        end.remove(8);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(1, start.max());
        assertEquals(4, end.min());
        assertEquals(4, end.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest3(CPSolver cp) {
        CPIntVar start = CPFactory.makeIntVar(cp, 0, 10);
        CPIntVar end = CPFactory.makeIntVar(cp, 0, 10);
        int[] array = {1,1,-1,-1,3,3,-1,-1,2,2};
        int value = -1;

        cp.post(new NoInBetween(array, start, end, value));

        assertEquals(1, start.min());
        assertEquals(8, end.max());

        start.remove(0);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(8, end.max());

        end.remove(8);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(1, start.max());
        assertEquals(4, end.min());
        assertEquals(4, end.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void Test4(CPSolver cp) {
        int[] array = {0,0,0,0,0,0,0,0,0,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0};
        CPIntVar start = CPFactory.makeIntVar(cp, 0, array.length);
        CPIntVar end = CPFactory.makeIntVar(cp, 0, array.length);
        int value = 1;

        cp.post(new NoInBetween(array, start, end, value));

        assertEquals(8, start.min());
        assertEquals(31, end.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void TestEnd(CPSolver cp) {
        int[] array = {0,0,0,1,1,1,0,0,0,1,1,1,-1};
        CPIntVar start = CPFactory.makeIntVar(cp, 0, array.length);
        CPIntVar end = CPFactory.makeIntVar(cp, 0, array.length);
        int value = 1;

        cp.post(new NoInBetween(array, start, end, value));

        assertEquals(2, start.min());
        assertEquals(12, end.max());

        start.removeBelow(6);
        cp.fixPoint();

        assertEquals(8, start.min());
        assertEquals(8, start.max());
        assertEquals(12, end.min());
        assertEquals(12, end.max());
    }

}
