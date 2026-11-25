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
 * <p>
 * The filtering is bounds consistent on the start, end and length of the interval. It builds the
 * layered graph over (position, state), in which each node has at most one outgoing arc since the
 * sequence is fixed. Following these arcs from a start gives the run of that start, and its accepted
 * ends are the accepting nodes on that run. The runs form a forest, which is visited depth-first
 * from the roots while the positions of the accepting nodes of the current path are kept, so each
 * start finds in O(1) its first and last accepted end within the end and length bounds. A call runs
 * in O(n |Q|) time.
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
    // the shortest path length from a start (UNREACHED when unreachable), the forest of runs
    // (children = predecessors on a run), its roots and the DFS stack.
    private final int[] fwdMinDist;
    private final int[] childHead;
    private final int[] childNext;
    private final int[] roots;
    private final int[] stack;
    // Along the current root path of the DFS: cntAt[p] = number of accepting nodes at positions >= p,
    // accPos[j] = position of the (j+1)-th accepting node from the root.
    private final int[] cntAt;
    private final int[] accPos;

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
            for (int j = 0; j < maxVal + 1; j++) {
                transitionFct[i][j] = A[i][j];
            }
        }

        int nodes = (n + 1) * nbStates;
        fwdMinDist = new int[nodes];
        childHead = new int[nodes];
        childNext = new int[nodes];
        roots = new int[nodes];
        stack = new int[nodes];
        cntAt = new int[n + 2];
        accPos = new int[n + 2];
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

    /**
     * Every node has at most one successor, so the nodes reached from the possible starts form a
     * forest whose roots are the nodes without a successor: the run of a start is the path from its
     * node (s, q0) to its root, and its accepted ends are the positions of the accepting nodes on that
     * path. A depth-first visit of each tree keeps, for the current path, the number of accepting
     * nodes at each position and above (cntAt) and their positions (accPos), so that each start reads
     * in O(1) its first accepted end >= a and its last accepted end <= b, where [a, b] are the ends
     * allowed by the end and length bounds for that start.
     */
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

        int lo = startMin;
        int hi = Math.min(endMax, n);
        int lastStart = Math.min(startMax, hi);
        int firstEnd = Math.max(endMin, 0);

        int minStart = Integer.MAX_VALUE, maxStart = -1;
        int minEnd = Integer.MAX_VALUE, maxEnd = -1;
        int minLen = Integer.MAX_VALUE, maxLen = -1;

        if (lo <= hi && hasFinal) {
            // 1. Forward sweep: the nodes reached from a possible start, with their shortest distance
            //    from one (a node at distance >= lengthMax has no useful successor), linked to their
            //    successor in the forest; a node without a successor is a root.
            Arrays.fill(fwdMinDist, lo * nbStates, (hi + 1) * nbStates, UNREACHED);
            Arrays.fill(childHead, lo * nbStates, (hi + 1) * nbStates, -1);
            for (int s = lo; s <= lastStart; s++) {
                fwdMinDist[s * nbStates + initialState] = 0;
            }
            int nRoots = 0;
            for (int i = lo; i <= hi; i++) {
                int layer = i * nbStates;
                int value = i < hi ? x[i] : -1;
                boolean validSymbol = value >= 0 && value < alphabetSize;
                for (int q = 0; q < nbStates; q++) {
                    int k = layer + q;
                    int dist = fwdMinDist[k];
                    if (dist == UNREACHED) continue;
                    int qNext = validSymbol ? transitionFct[q][value] : -1;
                    if (qNext < 0 || dist >= lengthMax) {
                        roots[nRoots++] = k;
                        continue;
                    }
                    int next = layer + nbStates + qNext;
                    if (dist + 1 < fwdMinDist[next]) fwdMinDist[next] = dist + 1;
                    childNext[k] = childHead[next];
                    childHead[next] = k;
                }
            }

            // 2. Depth-first visit of each tree. A child is one position below its parent, so the
            //    current path covers the positions [p, rootPos] and cntAt[p + 1] is the parent's count.
            for (int r = 0; r < nRoots; r++) {
                int root = roots[r];
                int rootPos = root / nbStates;
                int top = 0;
                stack[top++] = root;
                while (top > 0) {
                    int k = stack[--top];
                    int p = k / nbStates;
                    int q = k - p * nbStates;
                    int cnt = (p == rootPos ? 0 : cntAt[p + 1]) + (isFinal[q] ? 1 : 0);
                    cntAt[p] = cnt;
                    if (isFinal[q]) accPos[cnt - 1] = p; // accPos[0 .. cnt-1]: decreasing positions

                    if (q == initialState && p <= lastStart) {
                        // a possible start: its ends must lie in [a, b]
                        int a = lengthMin > hi - p ? hi + 1 : Math.max(firstEnd, p + lengthMin);
                        int b = lengthMax > hi - p ? hi : p + lengthMax;
                        if (a <= b && a <= rootPos) {
                            int atLeastA = cntAt[a]; // accepting nodes at positions >= a on the run
                            if (atLeastA > 0 && accPos[atLeastA - 1] <= b) {
                                int first = accPos[atLeastA - 1];
                                int aboveB = b + 1 > rootPos ? 0 : cntAt[b + 1];
                                int last = accPos[aboveB];
                                if (p < minStart) minStart = p;
                                if (p > maxStart) maxStart = p;
                                if (first < minEnd) minEnd = first;
                                if (last > maxEnd) maxEnd = last;
                                if (first - p < minLen) minLen = first - p;
                                if (last - p > maxLen) maxLen = last - p;
                            }
                        }
                    }

                    for (int c = childHead[k]; c >= 0; c = childNext[c]) {
                        stack[top++] = c;
                    }
                }
            }
        }

        if (maxStart == -1) {
            // No accepted window fits the bounds. For a present interval that is a dead end; for an
            // optional one it only means this branch cannot be taken, so mark it absent and let the
            // surrounding disjunction carry on.
            if (interval.isPresent()) {
                throw new InconsistencyException();
            }
            interval.setAbsent();
            setActive(false);
            return;
        }

        interval.setStartMin(minStart);
        interval.setStartMax(maxStart);
        interval.setEndMin(minEnd);
        interval.setEndMax(maxEnd);
        interval.setLengthMin(minLen);
        interval.setLengthMax(maxLen);
    }
}
