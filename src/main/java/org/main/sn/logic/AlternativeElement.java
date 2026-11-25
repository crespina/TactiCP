package org.main.sn.logic;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;

/**
 * {@code main = alts[chosen]}, every other alternative being absent: an alternative is present iff
 * {@code main} is present and it is the chosen one. When {@code main} is absent, every alternative
 * is absent and {@code chosen} takes {@code absentValue}, so an absent {@code main} gives a single
 * assignment; a negative {@code absentValue} means that {@code main} cannot be absent.
 * <p>
 * This is MaxiCP's {@code Alternative} with cardinality 1 whose choice is the index variable
 * {@code chosen} instead of the presence of the alternatives. Filtering, O(|alts|) per call:
 * <ul>
 *   <li>a value removed from {@code chosen} makes its alternative absent, and a present
 *       alternative is the chosen one;</li>
 *   <li>the bounds of {@code main} go into every alternative still possible, chosen or not (an
 *       alternative is either absent or equal to {@code main}), so an alternative that no longer
 *       fits becomes absent, and its value leaves {@code chosen};</li>
 *   <li>{@code main} is narrowed to the hull of the alternatives still possible, and is absent
 *       when there are none.</li>
 * </ul>
 */
public class AlternativeElement extends AbstractCPConstraint {

    private final CPIntervalVar main;
    private final CPIntervalVar[] alts;
    private final CPIntVar chosen;
    private final int absentValue;
    private final int[] values;

    public AlternativeElement(CPIntervalVar main, CPIntervalVar[] alts, CPIntVar chosen, int absentValue) {
        super(main.getSolver());
        if (absentValue >= alts.length) throw new IllegalArgumentException("absentValue must index an alternative");
        this.main = main;
        this.alts = alts;
        this.chosen = chosen;
        this.absentValue = absentValue;
        this.values = new int[Math.max(chosen.size(), alts.length)];
    }

    @Override
    public void post() {
        chosen.removeBelow(0);
        chosen.removeAbove(alts.length - 1);
        main.propagateOnChange(this);
        for (CPIntervalVar alt : alts) alt.propagateOnChange(this);
        chosen.propagateOnDomainChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        if (main.isAbsent()) {
            makeMainAbsent();
            return;
        }

        // the alternatives that cannot be chosen are absent; a present one is the chosen one
        for (int c = 0; c < alts.length; c++) {
            if (!chosen.contains(c)) {
                alts[c].setAbsent();
            } else if (alts[c].isPresent()) {
                main.setPresent();
                chosen.fix(c);
            }
        }

        // an alternative is absent or equal to main: it gets the bounds of main, and a value whose
        // alternative is absent leaves chosen (but for absentValue while main may still be absent)
        boolean present = main.isPresent();
        int size = chosen.fillArray(values);
        for (int i = 0; i < size; i++) {
            int c = values[i];
            CPIntervalVar alt = alts[c];
            if (!alt.isAbsent()) {
                alt.setStartMin(main.startMin());
                alt.setStartMax(main.startMax());
                alt.setEndMin(main.endMin());
                alt.setEndMax(main.endMax());
                alt.setLengthMin(main.lengthMin());
                alt.setLengthMax(main.lengthMax());
            }
            if (alt.isAbsent() && (present || c != absentValue)) chosen.remove(c);
        }

        // main lies in the hull of the alternatives still possible
        int startMin = Integer.MAX_VALUE, startMax = Integer.MIN_VALUE;
        int endMin = Integer.MAX_VALUE, endMax = Integer.MIN_VALUE;
        int lengthMin = Integer.MAX_VALUE, lengthMax = Integer.MIN_VALUE;
        boolean anyPossible = false;
        size = chosen.fillArray(values);
        for (int i = 0; i < size; i++) {
            CPIntervalVar alt = alts[values[i]];
            if (alt.isAbsent()) continue;
            anyPossible = true;
            startMin = Math.min(startMin, alt.startMin());
            startMax = Math.max(startMax, alt.startMax());
            endMin = Math.min(endMin, alt.endMin());
            endMax = Math.max(endMax, alt.endMax());
            lengthMin = Math.min(lengthMin, alt.lengthMin());
            lengthMax = Math.max(lengthMax, alt.lengthMax());
        }
        if (!anyPossible) {
            // only absentValue is left, and its alternative is absent
            main.setAbsent();
            makeMainAbsent();
            return;
        }
        main.setStartMin(startMin);
        main.setStartMax(startMax);
        main.setEndMin(endMin);
        main.setEndMax(endMax);
        main.setLengthMin(lengthMin);
        main.setLengthMax(lengthMax);
        if (main.isAbsent()) {
            makeMainAbsent();
            return;
        }

        if (chosen.isFixed()) {
            int c = chosen.min();
            if (c != absentValue) main.setPresent();
            if (main.isPresent()) alts[c].setPresent();
        }
    }

    private void makeMainAbsent() {
        for (CPIntervalVar alt : alts) alt.setAbsent();
        chosen.fix(absentValue); // fails if main cannot be absent
        setActive(false);
    }
}
