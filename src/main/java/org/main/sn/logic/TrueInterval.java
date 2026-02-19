package org.main.sn.logic;


import org.maxicp.cp.engine.core.*;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;

import static org.maxicp.cp.CPFactory.*;

/**
 *
 * True Interval Constraint modeling {@code if i ∈ interval  ⇒  array[i] = True}
 *
 */
public class TrueInterval extends AbstractCPConstraint {

    private final CPBoolVar[] t;

    private final StateInt low;
    private final StateInt up;

    private final CPIntervalVar interval;
    private CPIntVar end;
    private CPIntVar start;


    /**
     * Creates an TrueInterval constraint, i.e. if i ∈ interval  ⇒  array[i] = True
     * with a bound consistent filtering
     *
     * @param array the array to index
     * @param interval the interval variable
     */
    public TrueInterval(CPBoolVar[] array, CPIntervalVar interval) {
        super(interval.getSolver());
        this.t = array;

        StateManager sm = getSolver().getStateManager();
        low = sm.makeStateInt(0);
        up = sm.makeStateInt(t.length - 1);

        this.interval = interval;
    }

    @Override
    public void post() {

        CPSolver cp = interval.getSolver();
        interval.setEndMax(t.length);

        this.start = start(interval);
        this.end = end(interval);
        cp.post(lt(start, end));
        // remove fixed false values in the domain of start & end

        int sMin = interval.startMin();
        int sMax = interval.startMax();

        while (sMin <= sMax) {
            if (t[sMin].isFalse()) {
                start.remove(sMin);
            } else {
                break;
            }
            sMin++;
        }
        low.setValue(sMin);

        int eMin = interval.endMin();
        int eMax = interval.endMax();

        while (eMax >= eMin) {
            if (t[eMax-1].isFalse()) {
                end.remove(eMax);
            } else {
                break;
            }
            eMax--;
        }
        up.setValue(eMax);

        start.propagateOnDomainChange(this);
        end.propagateOnDomainChange(this);

        for (CPBoolVar var : t) {
            if (!var.isFixed())
                var.propagateOnDomainChange(this);
        }
        propagate();

    }

    @Override
    public void propagate() {

        // remove value at the edge of the domain
        int l = low.value();
        int u = up.value();

        while (l < u && t[l].isFalse()) {
            start.removeBelow(l+1);
            l++;
        }

        while (l < u && t[u-1].isFalse()) {
            end.removeAbove(u-1);
            u--;
        }

        low.setValue(l);
        up.setValue(u);

        // if start and end are fixed, enforce truth in between
        if (start.isFixed() && end.isFixed()) {
            int s = start.min();
            int e = end.min();
            for (int i = s; i < e; i++) {
                t[i].fix(true);
            }
        }

    }

}
