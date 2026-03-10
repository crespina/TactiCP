/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.main.sn;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.main.sn.logic.TrueInterval;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.CPFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.maxicp.cp.CPFactory.*;


public class TrueIntervalTest extends CPSolverTest {

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest0(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        interval.setPresent();
        CPIntVar start = start(interval);
        CPIntVar end = end(interval);
        CPBoolVar[] array = makeBoolVarArray(cp, 10);

        cp.post(new TrueInterval(array, interval));

        assertEquals(0, start.min());
        assertEquals(10, end.max());

        start.remove(0);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(9, start.max());
        assertEquals(2, end.min());
        assertEquals(10, end.max());

        end.removeAbove(6);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(5, start.max());
        assertEquals(2, end.min());
        assertEquals(6, end.max());

        start.fix(4);
        end.fix(6);
        cp.fixPoint();

        assertTrue(array[4].isTrue());
        assertTrue(array[5].isTrue());
        assertTrue(array[6].isTrue());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest1(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPBoolVar[] array = CPFactory.makeBoolVarArray(cp, 10);
        interval.setPresent();
        CPIntVar start = start(interval);
        CPIntVar end = end(interval);

        cp.post(new TrueInterval(array, interval));

        assertEquals(0, start.min());
        assertEquals(10, end.max()); //end is not included in the interval

        array[0].fix(false);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(9, start.max());
        assertEquals(2, end.min());
        assertEquals(10, end.max());

        array[2].fix(false);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(9, start.max());
        assertEquals(2, end.min());
        assertEquals(10, end.max());

        array[9].fix(false);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(8, start.max());
        assertEquals(2, end.min());
        assertEquals(9, end.max());

        array[7].fix(false);
        cp.fixPoint();

        assertEquals(1, start.min());
        assertEquals(8, start.max());
        assertEquals(2, end.min());
        assertEquals(9, end.max());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest2(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPBoolVar[] array = CPFactory.makeBoolVarArray(cp, 10);
        interval.setPresent();
        CPIntVar start = start(interval);
        CPIntVar end = end(interval);

        cp.post(new TrueInterval(array, interval));

        assertEquals(0, start.min());
        assertEquals(10, end.max());

        for (int i = 2; i <= 7; i++) {
            array[i].fix(false);
        }
        cp.fixPoint();

        assertEquals(0, start.min());
        assertEquals(9, start.max());
        assertEquals(1, end.min());
        assertEquals(10, end.max());

        array[1].fix(false);
        cp.fixPoint();

        assertEquals(0, start.min());
        assertEquals(9, start.max());
        assertEquals(1, end.min());
        assertEquals(10, end.max());

        array[0].fix(false);
        cp.fixPoint();

        assertEquals(8, start.min());
        assertEquals(9, start.max());
        assertEquals(9, end.min());
        assertEquals(10, end.max());

        array[8].fix(false);
        cp.fixPoint();

        assertTrue(array[9].isTrue());
    }

    @ParameterizedTest
    @MethodSource("getSolver")
    public void simpleTest3(CPSolver cp) {
        CPIntervalVar interval = makeIntervalVar(cp);
        CPBoolVar[] array = CPFactory.makeBoolVarArray(cp, 10);
        interval.setPresent();
        CPIntVar start = start(interval);
        CPIntVar end = end(interval);

        cp.post(new TrueInterval(array, interval));

        assertEquals(0, start.min());
        assertEquals(10, end.max());

        for (int i = 2; i <= 7; i++) {
            array[i].fix(true);
        }
        cp.fixPoint();

        interval.setStartMin(4);
        interval.setEndMax(6);
        cp.fixPoint();
        System.out.println(interval);
    }
}
