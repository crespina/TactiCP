package org.main.sn.logic;

import org.main.util.Automaton;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.util.exception.InconsistencyException;

import java.util.List;
import java.util.ArrayList;

/**
 * Regular Constraint for Interval Variables
 * Ensures that the subsequence x[interval.start ... interval.end-1]
 * is recognized by the automaton
 */
public class RegularInterval extends AbstractCPConstraint {
    private final int[] x; // fixed sequence
    private final CPIntervalVar interval; // variable representing start and end of matching subsequence
    private final int[][] transitionFct;
    private final int initialState;
    private final List<Integer> finalStates;
    private final int n; // length of the fixed sequence

    /**
     * Creates a regular constraint for intervals.
     * <p> This constraint holds iff the subsequence x[start...end-1]
     * where start = interval.startMin() and end = interval.endMin()
     * is a word recognized by the automaton.
     *
     * @param x a fixed array of integers
     * @param interval an interval variable (start and end positions in x)
     * @param automaton an automaton defined by its transition function, initial state and accepting states
     * The transition function is a 2D array: {states} x {domain values} -> {states}
     */
    public RegularInterval(int[] x, CPIntervalVar interval, Automaton automaton) {
        super(interval.getSolver());
        this.x = x;
        this.interval = interval;
        this.n = x.length;
        int nbStates = automaton.getAutomaton().length;
        this.initialState = automaton.getInitState();
        int[][] A = automaton.getAutomaton();

        assert ((initialState >= 0) && (initialState < nbStates));

        finalStates = new ArrayList<>();
        for (int state : automaton.getAcceptingStates()) {
            assert ((state >= 0) && (state < nbStates));
            finalStates.add(state);
        }

        // Find max value in sequence
        int maxVal = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            if (x[i] > maxVal) maxVal = x[i];
        }

        transitionFct = new int[nbStates][maxVal + 1];
        for (int i = 0; i < nbStates; i++) {
            assert (A[i].length == maxVal + 1);
            for (int j = 0; j < maxVal + 1; j++) {
                assert (A[i][j] < nbStates);
                transitionFct[i][j] = A[i][j];
            }
        }
    }

    @Override
    public void post() {
        interval.propagateOnChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // The interval must be present to satisfy the constraint
        if (interval.isAbsent()) {
            throw new InconsistencyException();
        }

        interval.setPresent();

        int startMin = interval.startMin();
        int startMax = interval.startMax();
        int endMin = interval.endMin();
        int endMax = interval.endMax();
        int lengthMin = interval.lengthMin();
        int lengthMax = interval.lengthMax();

        // Compute which (start, end) pairs are valid
        boolean foundValid = false;
        int minValidStart = n;
        int maxValidStart = -1;
        int minValidEnd = n + 1;
        int maxValidEnd = -1;
        int minValidLength = Integer.MAX_VALUE;
        int maxValidLength = -1;

        for (int start = startMin; start <= startMax && start < n; start++) {
            for (int end = Math.max(endMin, start + lengthMin);
                 end <= Math.min(endMax, start + lengthMax) && end <= n;
                 end++) {

                int length = end - start;
                if (length < lengthMin || length > lengthMax) continue;

                if (isValidSequence(start, end)) {
                    foundValid = true;
                    minValidStart = Math.min(minValidStart, start);
                    maxValidStart = Math.max(maxValidStart, start);
                    minValidEnd = Math.min(minValidEnd, end);
                    maxValidEnd = Math.max(maxValidEnd, end);
                    minValidLength = Math.min(minValidLength, length);
                    maxValidLength = Math.max(maxValidLength, length);
                }
            }
        }

        if (!foundValid) {
            throw new InconsistencyException();
        }

        // Update interval bounds
        interval.setStartMin(minValidStart);
        interval.setStartMax(maxValidStart);
        interval.setEndMin(minValidEnd);
        interval.setEndMax(maxValidEnd);
        interval.setLengthMin(minValidLength);
        interval.setLengthMax(maxValidLength);
    }

    /**
     * Check if the subsequence x[start...end-1] is accepted by the automaton
     */
    private boolean isValidSequence(int start, int end) {
        if (start < 0 || end < 0 || start > end || end > n) {
            return false;
        }
        if (start == end) {
            // Empty sequence - check if initial state is final
            return finalStates.contains(initialState);
        }

        int currentState = initialState;
        for (int i = start; i < end; i++) {
            int value = x[i];
            if (value >= transitionFct[currentState].length || value < 0) {
                return false;
            }
            int nextState = transitionFct[currentState][value];
            if (nextState < 0) {
                return false; // No valid transition
            }
            currentState = nextState;
        }

        return finalStates.contains(currentState);
    }
}