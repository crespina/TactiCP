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
        // An absent interval covers no position, so there is nothing to enforce.
        if (interval.isAbsent()) {
            setActive(false);
            return;
        }

        interval.setEndMax(t.length);
        // the interval is non-empty (it used to be stated as start < end); this also keeps
        // startMax below t.length, which the scans below index into
        interval.setLengthMin(Math.max(1, interval.lengthMin()));

        // trim the positions already known to be false off both ends of the window
        int sMin = interval.startMin();
        int sMax = Math.min(interval.startMax(), t.length - 1);
        while (sMin <= sMax && t[sMin].isFalse()) {
            sMin++;
        }
        low.setValue(sMin);

        int eMin = Math.max(interval.endMin(), 1);
        int eMax = interval.endMax();
        while (eMax >= eMin && t[eMax - 1].isFalse()) {
            eMax--;
        }
        up.setValue(eMax);

        interval.setStartMin(sMin);
        interval.setEndMax(eMax);

        interval.propagateOnChange(this);

        for (CPBoolVar var : t) {
            if (!var.isFixed())
                var.propagateOnDomainChange(this);
        }
        propagate();

    }

    @Override
    public void propagate() {

        if (interval.isAbsent()) {
            setActive(false);
            return;
        }

        // remove value at the edge of the domain
        int l = low.value();
        int u = up.value();

        while (l < u && t[l].isFalse()) {
            l++;
        }

        while (l < u && t[u-1].isFalse()) {
            u--;
        }

        low.setValue(l);
        up.setValue(u);

        // Filtering the interval directly (rather than reified start/end variables, which
        // CPFactory refuses to build for an optional interval) lets the engine turn an
        // over-constrained optional interval into an absent one instead of failing.
        interval.setStartMin(l);
        interval.setEndMax(u);

        // if start and end are fixed, enforce truth in between
        if (interval.startMin() == interval.startMax() && interval.endMin() == interval.endMax()) {
            int s = interval.startMin();
            int e = interval.endMin();
            for (int i = s; i < e; i++) {
                t[i].fix(true);
            }
        }

    }

}
