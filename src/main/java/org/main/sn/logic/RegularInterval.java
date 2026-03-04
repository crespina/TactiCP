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

        finalStates = new ArrayList<>();
        for (int state : automaton.getAcceptingStates()) {
            finalStates.add(state);
        }

        // Find max value in sequence
        int maxVal = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            if (x[i] > maxVal) maxVal = x[i];
        }

        transitionFct = new int[nbStates][maxVal + 1];
        for (int i = 0; i < nbStates; i++) {
            //assert (A[i].length == maxVal + 1);
            for (int j = 0; j < maxVal + 1; j++) {
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

        int startMin = interval.startMin();
        int startMax = interval.startMax();
        int endMin = interval.endMin();
        int endMax = interval.endMax();
        int lengthMin = interval.lengthMin();
        int lengthMax = interval.lengthMax();

        int nbStates = transitionFct.length;

        // ---------------------------------------------------------------
        // 1. Build the layered DAG and compute forward reachability.
        //
        //    Layer i (0 <= i <= n) contains one node per automaton state.
        //    Node (i, q) is reachable from the source iff there exists a
        //    valid start position s in [startMin, startMax] such that
        //    feeding x[s..i-1] into the automaton from initialState
        //    reaches state q, with path length (i - s) <= lengthMax.
        //
        //    For each reachable (i, q) we track:
        //      fwdMinDist[i][q] = minimum path length from any source
        //      fwdMaxDist[i][q] = maximum path length from any source
        // ---------------------------------------------------------------

        boolean[][] fwdReachable = new boolean[n + 1][nbStates];
        int[][] fwdMinDist = new int[n + 1][nbStates];
        int[][] fwdMaxDist = new int[n + 1][nbStates];

        for (int i = 0; i <= n; i++) {
            for (int q = 0; q < nbStates; q++) {
                fwdMinDist[i][q] = Integer.MAX_VALUE;
                fwdMaxDist[i][q] = -1;
            }
        }

        // Seed: every valid start position s injects (s, initialState) with distance 0
        boolean[] isSeeded = new boolean[n + 1];
        for (int s = startMin; s <= Math.min(startMax, n); s++) {
            fwdReachable[s][initialState] = true;
            fwdMinDist[s][initialState] = Math.min(fwdMinDist[s][initialState], 0);
            fwdMaxDist[s][initialState] = Math.max(fwdMaxDist[s][initialState], 0);
            isSeeded[s] = true;
        }

        // Forward pass: propagate through transitions
        for (int i = 0; i < n; i++) {
            int value = x[i];
            for (int q = 0; q < nbStates; q++) {
                if (!fwdReachable[i][q]) continue;
                if (value < 0 || value >= transitionFct[q].length) continue;
                int qNext = transitionFct[q][value];
                if (qNext < 0) continue;
                int newMinDist = fwdMinDist[i][q] + 1;
                int newMaxDist = fwdMaxDist[i][q] + 1;
                // Prune paths that already exceed lengthMax
                if (newMinDist > lengthMax) continue;
                fwdReachable[i + 1][qNext] = true;
                fwdMinDist[i + 1][qNext] = Math.min(fwdMinDist[i + 1][qNext], newMinDist);
                fwdMaxDist[i + 1][qNext] = Math.max(fwdMaxDist[i + 1][qNext], newMaxDist);
            }
        }

        // ---------------------------------------------------------------
        // 2. Backward reachability from the sink.
        //
        //    The sink is reachable from (e, q) if q is a final state
        //    and e is in [endMin, endMax].
        //
        //    For each backward-reachable (i, q) we track:
        //      bwdMinDist[i][q] = minimum path length to the sink
        //      bwdMaxDist[i][q] = maximum path length to the sink
        // ---------------------------------------------------------------

        boolean[][] bwdReachable = new boolean[n + 1][nbStates];
        int[][] bwdMinDist = new int[n + 1][nbStates];
        int[][] bwdMaxDist = new int[n + 1][nbStates];

        for (int i = 0; i <= n; i++) {
            for (int q = 0; q < nbStates; q++) {
                bwdMinDist[i][q] = Integer.MAX_VALUE;
                bwdMaxDist[i][q] = -1;
            }
        }

        // Seed: every valid end position e with a final state
        boolean[] isSinkSeeded = new boolean[n + 1];
        for (int e = Math.max(endMin, 0); e <= Math.min(endMax, n); e++) {
            for (int qf : finalStates) {
                if (qf >= 0 && qf < nbStates) {
                    bwdReachable[e][qf] = true;
                    bwdMinDist[e][qf] = 0;
                    bwdMaxDist[e][qf] = 0;
                    isSinkSeeded[e] = true;
                }
            }
        }

        // Backward pass: propagate backwards through transitions
        for (int i = n - 1; i >= 0; i--) {
            int value = x[i];
            for (int q = 0; q < nbStates; q++) {
                if (value < 0 || value >= transitionFct[q].length) continue;
                int qNext = transitionFct[q][value];
                if (qNext < 0) continue;
                if (!bwdReachable[i + 1][qNext]) continue;
                int newMinDist = bwdMinDist[i + 1][qNext] + 1;
                int newMaxDist = bwdMaxDist[i + 1][qNext] + 1;
                // Prune paths that already exceed lengthMax
                if (newMinDist > lengthMax) continue;
                bwdReachable[i][q] = true;
                bwdMinDist[i][q] = Math.min(bwdMinDist[i][q], newMinDist);
                bwdMaxDist[i][q] = Math.max(bwdMaxDist[i][q], newMaxDist);
            }
        }

        // ---------------------------------------------------------------
        // 3. Compute bounds from surviving nodes.
        //
        //    A node (i, q) survives iff it is both forward- and backward-
        //    reachable AND the total path length (fwdDist + bwdDist)
        //    can fall within [lengthMin, lengthMax].
        //
        //    From surviving nodes we derive:
        //      - valid starts: layer i where fwdMinDist[i][q] == 0
        //        (i.e. i is a source/start position)
        //      - valid ends:   layer i where bwdMinDist[i][q] == 0
        //        (i.e. i is a sink/end position)
        //      - valid lengths: fwdMinDist + bwdMinDist .. fwdMaxDist + bwdMaxDist
        // ---------------------------------------------------------------

        int minValidStart = n;
        int maxValidStart = -1;
        int minValidEnd = n + 1;
        int maxValidEnd = -1;
        int minValidLength = Integer.MAX_VALUE;
        int maxValidLength = -1;

        for (int i = 0; i <= n; i++) {
            for (int q = 0; q < nbStates; q++) {
                if (!fwdReachable[i][q] || !bwdReachable[i][q]) continue;

                // Total path length range through this node
                int totalMinLen = fwdMinDist[i][q] + bwdMinDist[i][q];
                int totalMaxLen = fwdMaxDist[i][q] + bwdMaxDist[i][q];

                // Check feasibility with length constraints
                if (totalMinLen > lengthMax || totalMaxLen < lengthMin) continue;

                // Clamp to valid length range
                int clampedMin = Math.max(totalMinLen, lengthMin);
                int clampedMax = Math.min(totalMaxLen, lengthMax);

                minValidLength = Math.min(minValidLength, clampedMin);
                maxValidLength = Math.max(maxValidLength, clampedMax);

                // This node is a valid start position if it was seeded as a source
                if (isSeeded[i] && q == initialState) {
                    // i is a start position; check that the path from here can
                    // reach the sink with a valid length
                    if (bwdMinDist[i][q] <= lengthMax && bwdMaxDist[i][q] >= lengthMin) {
                        int startClampedMin = Math.max(bwdMinDist[i][q], lengthMin);
                        int startClampedMax = Math.min(bwdMaxDist[i][q], lengthMax);
                        if (startClampedMin <= startClampedMax) {
                            minValidStart = Math.min(minValidStart, i);
                            maxValidStart = Math.max(maxValidStart, i);
                        }
                    }
                }

                // This node is a valid end position if it was seeded as a sink
                if (isSinkSeeded[i] && finalStates.contains(q)) {
                    // i is an end position; check that a path from a source
                    // can reach here with a valid length
                    if (fwdMinDist[i][q] <= lengthMax && fwdMaxDist[i][q] >= lengthMin) {
                        int endClampedMin = Math.max(fwdMinDist[i][q], lengthMin);
                        int endClampedMax = Math.min(fwdMaxDist[i][q], lengthMax);
                        if (endClampedMin <= endClampedMax) {
                            minValidEnd = Math.min(minValidEnd, i);
                            maxValidEnd = Math.max(maxValidEnd, i);
                        }
                    }
                }
            }
        }

        if (maxValidStart == -1 || maxValidEnd == -1 || maxValidLength == -1) {
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
}