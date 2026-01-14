package org.main.sn.logic;


import org.maxicp.cp.engine.constraints.LessOrEqual;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;

/**
 *
 * True Interval Constraint modeling {@code if start <= i <= end  ⇒  array[i] = True}
 *
 */
public class TrueInterval extends AbstractCPConstraint {

    private final CPBoolVar[] t;

    private final StateInt low;
    private final StateInt up;

    private final CPIntVar start;
    private final CPIntVar end;


    /**
     * Creates an TrueInterval constraint, i.e. if start <= i <= end  ⇒  array[i] = True
     * with a domain consistent filtering
     *
     * @param array the array to index
     * @param start the index variable of start
     * @param end   the index variable of end
     */
    public TrueInterval(CPBoolVar[] array, CPIntVar start, CPIntVar end) {
        super(start.getSolver());
        this.t = array;

        StateManager sm = getSolver().getStateManager();
        low = sm.makeStateInt(0);
        up = sm.makeStateInt(t.length - 1);

        this.start = start;
        this.end = end;
    }

    @Override
    public void post() {

        start.removeBelow(0);
        start.removeAbove(end.max());
        end.removeBelow(start.min());
        end.removeAbove(t.length - 1);

        start.getSolver().post(new LessOrEqual(start, end));

        start.propagateOnDomainChange(this);
        end.propagateOnDomainChange(this);

        for (CPIntVar var : t) {
            if (!var.isFixed())
                var.propagateOnDomainChange(this);
        }
        propagate();

    }

    @Override
    public void propagate() {
        //first enforce less
        start.removeAbove(end.max() - 1);
        end.removeBelow(start.min() + 1);
        if (start.max() < end.min())
            setActive(false);

        // remove fixed false values in the domain of start & end
        for (int i = start.min(); i <= start.max(); i++) {
            if (t[i].isFalse()) {
                start.remove(i);
            }
        }
        for (int i = end.min(); i <= end.max(); i++) {
            if (t[i].isFalse()) {
                end.remove(i);
            }
        }

        // remove value at the edge of the domain
        int l = low.value();
        int u = up.value();

        while (l <= u && t[l].isFalse()) {
            start.removeBelow(l);
            l++;
        }

        while (l <= u && t[u].isFalse()) {
            end.removeAbove(u);
            u--;
        }

        int sMin = start.min();
        int sMax = start.max();
        int eMin = end.min();
        int eMax = end.max();

        // remove unsupported start values
        for (int v = sMin; v <= sMax; v++) {
            if (!start.contains(v)) continue; // keep only values currently in domain
            boolean supported = false;
            // find an end w in end's domain with w > v such that all i in (v,w) can be true
            for (int w = Math.max(eMin, v + 1); w <= eMax && !supported; w++) {
                if (!end.contains(w)) continue;
                boolean ok = true;
                for (int i = v + 1; i < w; i++) {
                    if (t[i].isFalse()) { ok = false; break; }
                }
                if (ok) supported = true;
            }
            if (!supported) start.remove(v);
        }

        // remove unsupported end values
        for (int w = eMin; w <= eMax; w++) {
            if (!end.contains(w)) continue;
            boolean supported = false;
            for (int v = sMin; v <= Math.min(sMax, w - 1) && !supported; v++) {
                if (!start.contains(v)) continue;
                boolean ok = true;
                for (int i = v + 1; i < w; i++) {
                    if (t[i].isFalse()) { ok = false; break; }
                }
                if (ok) supported = true;
            }
            if (!supported) end.remove(w);
        }

        low.setValue(l);
        up.setValue(u);

        // if start and end are fixed, enforce truth in between
        if (start.isFixed() && end.isFixed()) {
            int s = start.min();
            int e = end.min();
            for (int i = s; i <= e; i++) {
                t[i].fix(true);
            }
        }

    }

}
