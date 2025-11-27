package org.util;

import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.BoolVar;

import static org.maxicp.cp.CPFactory.*;
import static org.util.And.*;

public class Overlap {
    private Overlap() {
        throw new UnsupportedOperationException();
    }

    //one vertex of B is inside of A
    public static CPBoolVar overlapping(CPIntVar ax1, CPIntVar ax2, CPIntVar ay1, CPIntVar ay2, CPIntVar bx1, CPIntVar bx2, CPIntVar by1, CPIntVar by2) {
        return and(isGe(bx2,ax1),isLe(bx1,ax2), isGe(by2, ay1), isLe(by1, ay2));
    }

    public static CPBoolVar overlapping(CPIntVar ax1, CPIntVar ax2, CPIntVar ay1, CPIntVar ay2, CPIntVar bx1, CPIntVar bx2, CPIntVar by1, CPIntVar by2, int tol) {
        return and(isGe(plus(bx2,tol),ax1),isLe(bx1,plus(ax2,tol)), isGe(plus(by2,tol), ay1), isLe(by1, plus(ay2,tol)));
    }

    public static CPBoolVar overlapping(CPIntVar ax1, CPIntVar ax2, CPIntVar ay1, CPIntVar ay2, int bx1, int bx2, int by1, int by2, int tol) {
        return and(isLe(ax1,bx2+tol),isGe(plus(ax2,tol), bx1), isLe(ay1, by2+tol), isGe(plus(ay2,tol), by1));
    }

    public static CPBoolVar overlapping(int ax1, int ax2, int ay1, int ay2, int bx1, int bx2, int by1, int by2, int tol, CPSolver cp) {
        boolean value = ax1 <= bx2+tol && ax2+tol >= bx1 && ay1<=by2+tol && ay2+tol>=by1;
        CPBoolVar b = makeBoolVar(cp);
        b.fix(value);
        return b;
    }
}
