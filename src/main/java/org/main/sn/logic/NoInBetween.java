package org.main.sn.logic;


import org.maxicp.cp.engine.constraints.LessOrEqual;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.state.StateInt;
import org.maxicp.state.StateManager;

/**
 *
 * No in between Constraint modeling {@code if start < i < end  ⇒  array[i] = -1}
 *
 */
public class NoInBetween extends AbstractCPConstraint {

    private final int[] t;

    private final StateInt low;
    private final StateInt up;

    private final CPIntVar start;
    private final CPIntVar end;


    /**
     * Creates an noInBetween constraint, i.e. if start < i < end  ⇒  array[i] = -1
     * with a domain consistent filtering
     *
     * @param array the array to index
     * @param start the index variable of start
     * @param end   the index variable of end
     */
    public NoInBetween(int[] array, CPIntVar start, CPIntVar end) {
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
        propagate();

    }

    @Override
    public void propagate() {
        //first enforce less
        start.removeAbove(end.max() - 1);
        end.removeBelow(start.min() + 1);
        if (start.max() < end.min())
            setActive(false);

        //if either one is fixed, then we can fix the other
        if (start.isFixed()) {
            end.fix(end.min());
        }
        if (end.isFixed()) {
            start.fix(start.max());
        }


        int sMin = start.min();
        int eMax = end.max();

        //first remove index where array[index] == -1
        for (int i = sMin + 1; i < eMax; i++) {
            if (t[i] == -1) {
                start.remove(i);
                end.remove(i);
            }
        }

        //then remove value at the edge of the domain
        int l = low.value();
        int u = up.value();

        while (l <= u && t[l] != -1) {
            start.removeBelow(l);
            l++;
        }

        while (l <= u && t[u] != -1) {
            end.removeAbove(u);
            u--;
        }

        low.setValue(l);
        up.setValue(u);

    }

}
