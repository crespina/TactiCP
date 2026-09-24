package org.main.sn.logic;

import org.main.util.Automaton;
import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.util.exception.InconsistencyException;

import java.util.Arrays;

/**
 * Regular Constraint for Interval Variables
 * Ensures that the subsequence x[interval.start ... interval.end-1]
 * is recognized by the automaton
 */
public class SubstringRegular extends AbstractCPConstraint {
    private static final int UNREACHED = Integer.MAX_VALUE;

    private final int[] x; // fixed sequence
    private final CPIntervalVar interval; // variable representing start and end of matching subsequence
    private final int[][] transitionFct;
    private final int initialState;
    private final boolean[] isFinal;
    private final boolean hasFinal;
    private final int n; // length of the fixed sequence
    private final int nbStates;
    private final int alphabetSize; // symbols 0..alphabetSize-1 have a transition column

    // Layered DAG over (position, state), flattened as i * nbStates + q and reused across calls:
    // min/max path length from a start (fwd) and to an end (bwd), UNREACHED / -1 when unreachable.
    private final int[] fwdMinDist;
    private final int[] fwdMaxDist;
    private final int[] bwdMinDist;
    private final int[] bwdMaxDist;

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
    public SubstringRegular(int[] x, CPIntervalVar interval, Automaton automaton) {
        super(interval.getSolver());
        this.x = x;
        this.interval = interval;
        this.n = x.length;
        this.nbStates = automaton.getAutomaton().length;
        this.initialState = automaton.getInitState();
        int[][] A = automaton.getAutomaton();

        isFinal = new boolean[nbStates];
        boolean anyFinal = false;
        for (int state : automaton.getAcceptingStates()) {
            if (state >= 0 && state < nbStates) {
                isFinal[state] = true;
                anyFinal = true;
            }
        }
        hasFinal = anyFinal;

        // Find max value in sequence
        int maxVal = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            if (x[i] > maxVal) maxVal = x[i];
        }

        alphabetSize = maxVal + 1;
        transitionFct = new int[nbStates][maxVal + 1];
        for (int i = 0; i < nbStates; i++) {
            //assert (A[i].length == maxVal + 1);
            for (int j = 0; j < maxVal + 1; j++) {
                transitionFct[i][j] = A[i][j];
            }
        }

        fwdMinDist = new int[(n + 1) * nbStates];
        fwdMaxDist = new int[(n + 1) * nbStates];
        bwdMinDist = new int[(n + 1) * nbStates];
        bwdMaxDist = new int[(n + 1) * nbStates];
    }

    /** The DAG pass costs O(n |Q|): let the cheap propagators reach their fixpoint first. */
    @Override
    public int priority() {
        return 1;
    }

    @Override
    public void post() {
        interval.propagateOnChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        // An absent interval carries no match obligation: the constraint is vacuously true.
        // Only "absent" means that -- an optional interval whose status is still undecided is
        // filtered as if present, which is sound because its bounds only ever matter in a
        // solution where it turns out to be present.
        if (interval.isAbsent()) {
            setActive(false);
            return;
        }

        int startMin = interval.startMin();
        int startMax = interval.startMax();
        int endMin = interval.endMin();
        int endMax = interval.endMax();
        int lengthMin = interval.lengthMin();
        int lengthMax = interval.lengthMax();

        // ---------------------------------------------------------------
        // 1. Build the layered DAG and compute forward reachability.
        //
        //    Layer i (0 <= i <= n) contains one node per automaton state.
        //    Node (i, q) is reachable from the source iff there exists a
        //    valid start position s in [startMin, startMax] such that
        //    feeding x[s..i-1] into the automaton from initialState
        //    reaches state q, with path length (i - s) <= lengthMax.
        //
        //    Only layers lo..hi can lie on a path from a start to an end,
        //    so the passes below never look outside [startMin, endMax].
        //
        //    For each reachable (i, q) we track:
        //      fwdMinDist[i][q] = minimum path length from any source
        //      fwdMaxDist[i][q] = maximum path length from any source
        // ---------------------------------------------------------------

        int lo = startMin;
        int hi = Math.min(endMax, n);
        int lastStart = Math.min(startMax, n);  // starts are the layers lo..lastStart
        int firstEnd = Math.max(endMin, 0);     // ends are the layers firstEnd..hi

        if (lo <= hi) {
            Arrays.fill(fwdMinDist, lo * nbStates, (hi + 1) * nbStates, UNREACHED);
            Arrays.fill(fwdMaxDist, lo * nbStates, (hi + 1) * nbStates, -1);
            Arrays.fill(bwdMinDist, lo * nbStates, (hi + 1) * nbStates, UNREACHED);
            Arrays.fill(bwdMaxDist, lo * nbStates, (hi + 1) * nbStates, -1);
        }

        // Seed: every valid start position s injects (s, initialState) with distance 0
        for (int s = lo; s <= Math.min(lastStart, hi); s++) {
            fwdMinDist[s * nbStates + initialState] = 0;
            fwdMaxDist[s * nbStates + initialState] = 0;
        }

        // Forward pass: propagate through transitions
        for (int i = lo; i < hi; i++) {
            int value = x[i];
            if (value < 0 || value >= alphabetSize) continue;
            int layer = i * nbStates;
            for (int q = 0; q < nbStates; q++) {
                int minDist = fwdMinDist[layer + q];
                if (minDist == UNREACHED) continue;
                int qNext = transitionFct[q][value];
                if (qNext < 0) continue;
                int newMinDist = minDist + 1;
                // Prune paths that already exceed lengthMax
                if (newMinDist > lengthMax) continue;
                int newMaxDist = fwdMaxDist[layer + q] + 1;
                int next = layer + nbStates + qNext;
                if (newMinDist < fwdMinDist[next]) fwdMinDist[next] = newMinDist;
                if (newMaxDist > fwdMaxDist[next]) fwdMaxDist[next] = newMaxDist;
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

        // Seed: every valid end position e with a final state
        if (hasFinal) {
            for (int e = Math.max(firstEnd, lo); e <= hi; e++) {
                for (int qf = 0; qf < nbStates; qf++) {
                    if (isFinal[qf]) {
                        bwdMinDist[e * nbStates + qf] = 0;
                        bwdMaxDist[e * nbStates + qf] = 0;
                    }
                }
            }
        }

        // Backward pass: propagate backwards through transitions
        for (int i = hi - 1; i >= lo; i--) {
            int value = x[i];
            if (value < 0 || value >= alphabetSize) continue;
            int layer = i * nbStates;
            for (int q = 0; q < nbStates; q++) {
                int qNext = transitionFct[q][value];
                if (qNext < 0) continue;
                int minDist = bwdMinDist[layer + nbStates + qNext];
                if (minDist == UNREACHED) continue;
                int newMinDist = minDist + 1;
                // Prune paths that already exceed lengthMax
                if (newMinDist > lengthMax) continue;
                int newMaxDist = bwdMaxDist[layer + nbStates + qNext] + 1;
                if (newMinDist < bwdMinDist[layer + q]) bwdMinDist[layer + q] = newMinDist;
                if (newMaxDist > bwdMaxDist[layer + q]) bwdMaxDist[layer + q] = newMaxDist;
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

        for (int i = lo; i <= hi; i++) {
            boolean isStart = i <= lastStart;
            boolean isEnd = hasFinal && i >= firstEnd;
            int layer = i * nbStates;
            for (int q = 0; q < nbStates; q++) {
                int k = layer + q;
                if (fwdMinDist[k] == UNREACHED || bwdMinDist[k] == UNREACHED) continue;

                // Total path length range through this node
                int totalMinLen = fwdMinDist[k] + bwdMinDist[k];
                int totalMaxLen = fwdMaxDist[k] + bwdMaxDist[k];

                // Check feasibility with length constraints
                if (totalMinLen > lengthMax || totalMaxLen < lengthMin) continue;

                // Clamp to valid length range
                int clampedMin = Math.max(totalMinLen, lengthMin);
                int clampedMax = Math.min(totalMaxLen, lengthMax);

                minValidLength = Math.min(minValidLength, clampedMin);
                maxValidLength = Math.max(maxValidLength, clampedMax);

                // This node is a valid start position if it was seeded as a source
                if (isStart && q == initialState) {
                    // i is a start position; check that the path from here can
                    // reach the sink with a valid length
                    if (bwdMinDist[k] <= lengthMax && bwdMaxDist[k] >= lengthMin) {
                        int startClampedMin = Math.max(bwdMinDist[k], lengthMin);
                        int startClampedMax = Math.min(bwdMaxDist[k], lengthMax);
                        if (startClampedMin <= startClampedMax) {
                            minValidStart = Math.min(minValidStart, i);
                            maxValidStart = Math.max(maxValidStart, i);
                        }
                    }
                }

                // This node is a valid end position if it was seeded as a sink
                if (isEnd && isFinal[q]) {
                    // i is an end position; check that a path from a source
                    // can reach here with a valid length
                    if (fwdMinDist[k] <= lengthMax && fwdMaxDist[k] >= lengthMin) {
                        int endClampedMin = Math.max(fwdMinDist[k], lengthMin);
                        int endClampedMax = Math.min(fwdMaxDist[k], lengthMax);
                        if (endClampedMin <= endClampedMax) {
                            minValidEnd = Math.min(minValidEnd, i);
                            maxValidEnd = Math.max(maxValidEnd, i);
                        }
                    }
                }
            }
        }

        if (maxValidStart == -1 || maxValidEnd == -1 || maxValidLength == -1) {
            // No window of the sequence is accepted by the automaton. For a present interval
            // that is a dead end; for an optional one it only means this branch cannot be
            // taken, so mark it absent and let the surrounding disjunction carry on.
            if (interval.isPresent()) {
                throw new InconsistencyException();
            }
            interval.setAbsent();
            setActive(false);
            return;
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
