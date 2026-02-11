package org.main.sn.logic;

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.util.exception.InconsistencyException;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Regular Constraint for Interval Variables
 * Ensures that the subsequence x[interval.start ... interval.end-1]
 * is recognized by the automaton
 */
public class RegularInterval extends AbstractCPConstraint {
    private int[] x; // fixed sequence
    private CPIntervalVar interval; // variable representing start and end of matching subsequence
    private int[][] transitionFct;
    private int initialState;
    private List<Integer> finalStates;
    private int n; // length of the fixed sequence
    private int nbStates;
    private double[][] ip; // ip[i][j]>0 for state j reached by reading x[0]..x[i-1] from the initial state
    private double[][] op; // op[i][j]>0 for state j reaching a final state by reading x[i]..x[end-1]

    /**
     * Creates a regular constraint for intervals.
     * <p> This constraint holds iff the subsequence x[start...end-1]
     * where start = interval.startMin() and end = interval.endMin()
     * is a word recognized by the automaton.
     *
     * @param x a fixed array of integers
     * @param interval an interval variable (start and end positions in x)
     * @param A a 2D array giving the transition function: {states} x {domain values} -> {states}
     * @param s the initial state
     * @param f a list of accepting states
     */
    public RegularInterval(int[] x, CPIntervalVar interval, int[][] A, int s, List<Integer> f) {
        super(interval.getSolver());
        this.x = x;
        this.interval = interval;
        this.n = x.length;
        this.nbStates = A.length;
        this.initialState = s;

        assert ((initialState >= 0) && (initialState < nbStates));

        finalStates = new ArrayList<Integer>();
        Iterator<Integer> itr = f.iterator();
        while (itr.hasNext()) {
            int state = itr.next();
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

        ip = new double[n + 1][nbStates];
        op = new double[n + 1][nbStates];
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
     * Check if the subsequence x[start..end-1] is accepted by the automaton
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