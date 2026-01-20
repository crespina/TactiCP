package org.main.sn.logic;


import org.maxicp.cp.engine.constraints.LessOrEqual;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

/**
 *
 * No in between Constraint modeling {@code if start < i < end  ⇒  array[i] = -1}
 *
 */
public class NoInBetween extends AbstractCPConstraint {

    private final int[] t;

    private final CPIntVar start;
    private final CPIntVar end;
    private final int value;


    /**
     * Creates an noInBetween constraint, i.e. if start < i < end  ⇒  array[i] = value
     * with a domain consistent filtering
     *
     * @param array the array to index
     * @param start the index variable of start
     * @param end   the index variable of end
     */
    public NoInBetween(int[] array, CPIntVar start, CPIntVar end, int value) {
        super(start.getSolver());
        this.t = array;

        this.start = start;
        this.end = end;
        this.value = value;
    }

    @Override
    public void post() {
        // basic bounds so start+1 and end-1 stay in array
        start.removeBelow(0);
        start.removeAbove(t.length - 2); // start <= n-2 (so start+1 exists)
        end.removeBelow(2);              // end >= 2 (so end-1 >= 1)
        end.removeAbove(t.length - 1);

        // keep only starts that are immediately BEFORE a value-block:
        for (int i = 0; i <= t.length - 2; i++) {
            if (!(t[i] != value && t[i + 1] == value)) {
                start.remove(i);
            }
        }

        // keep only ends that are immediately AFTER a value-block:
        for (int j = 1; j <= t.length - 1; j++) {
            if (!(t[j] != value && t[j - 1] == value)) {
                end.remove(j);
            }
        }

        // enforce ordering (still useful) and propagation triggers
        start.getSolver().post(new LessOrEqual(start, end));
        start.propagateOnDomainChange(this);
        end.propagateOnDomainChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // enforce at least one interior index: end >= start + 2
        start.removeAbove(end.max() - 2);
        end.removeBelow(start.min() + 2);

        // if the constraint is entailed, deactivate
        if (start.max() < end.min())
            setActive(false);

        // tighten the other side if one is fixed
        if (start.isFixed()) {
            end.fix(end.min());
        }
        if (end.isFixed()) {
            start.fix(start.max());
        }

        // additionally, remove any remaining starts/ends that violate the immediate-before/after condition
        // (this keeps domains clean if array is static)
        for (int i = start.min(); i <= start.max(); i++) {
            if (i <= t.length - 2 && !(t[i] != value && t[i + 1] == value)) {
                start.remove(i);
            }
        }
        for (int j = end.min(); j <= end.max(); j++) {
            if (j >= 1 && !(t[j] != value && t[j - 1] == value)) {
                end.remove(j);
            }
        }
    }

}
