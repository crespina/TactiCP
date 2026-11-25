package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.util.Automaton;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;

/**
 * {@link SubstringRegular} on small random instances, against the accepted windows enumerated by
 * brute force. The filtering must give exactly the bounds of the accepted windows that fit the
 * bounds of the interval (bounds consistency), and make an optional interval absent, or fail on a
 * present one, when there is none.
 */
public class SubstringRegularTest {

    /** An automaton, the alphabet of its words, and whether its words are padded as in the models. */
    record Lang(String name, Automaton automaton, int alphabet, boolean padded) {}

    /** The bounds of the interval: startMin, startMax, endMin, endMax, lengthMin, lengthMax. */
    record Outcome(boolean failed, boolean absent, int[] bounds) {
        static final Outcome FAILURE = new Outcome(true, false, null);
        static final Outcome ABSENT = new Outcome(false, true, null);
    }

    /** Accepts the runs of 1 whose length is 4 + 5k: the counter-example to the relaxed filtering. */
    static Automaton fourPlusFiveK() {
        int[][] t = new int[5][2];
        for (int[] row : t) Arrays.fill(row, -1);
        for (int q = 0; q < 5; q++) t[q][1] = (q + 1) % 5;
        return new Automaton(0, List.of(4), t);
    }

    /** The automata of the paper's models, on small alphabets, and the counter-example. */
    static List<Lang> paperLanguages() {
        return List.of(
                new Lang("1+", Automaton.APLUS(1, Set.of(1)), 2, false),
                new Lang("run", Automaton.NOTA_APLUS_NOTA(1, Set.of(1)), 2, true),
                new Lang("stay", Automaton.NOTX_XPLUS_NOTX(2, Set.of(1, 2)), 3, true),
                new Lang("pass min", Automaton.A_0PLUS_B(3, Set.of(1, 2), Set.of(1, 2, 3)), 4, false),
                new Lang("pass", Automaton.PAD_APLUS_0PLUS_BPLUS_PAD(3, Set.of(1, 2), Set.of(1, 2, 3)), 4, true),
                new Lang("move min", Automaton.A_NOTBSTAR_B(2, 1, 2), 3, false),
                new Lang("move", Automaton.PAD_APLUS_NOTABSTAR_BPLUS_PAD(2, 1, 2), 3, true),
                new Lang("4+5k", fourPlusFiveK(), 2, false));
    }

    static Lang randomLanguage(Random r) {
        int states = 2 + r.nextInt(5);
        int alphabet = 2 + r.nextInt(2);
        int[][] t = new int[states][alphabet];
        for (int q = 0; q < states; q++) {
            for (int a = 0; a < alphabet; a++) t[q][a] = r.nextInt(5) == 0 ? -1 : r.nextInt(states);
        }
        List<Integer> accepting = new ArrayList<>();
        for (int q = 0; q < states; q++) if (r.nextInt(5) < 2) accepting.add(q);
        if (accepting.isEmpty()) accepting.add(states - 1);
        return new Lang("random", new Automaton(0, accepting, t), alphabet, false);
    }

    static int[] randomWord(Random r, Lang lang) {
        int[] w = new int[1 + r.nextInt(14)];
        for (int i = 0; i < w.length; i++) w[i] = r.nextInt(lang.alphabet());
        // the automaton's table must have a column for every symbol of the word
        if (lang.padded()) w = Automaton.pad(w);
        return w;
    }

    static int[] randomBounds(Random r, int n) {
        int sMin = r.nextInt(n + 1), sMax = sMin + r.nextInt(n + 1 - sMin);
        int eMin = r.nextInt(n + 1), eMax = eMin + r.nextInt(n + 1 - eMin);
        int lMin = r.nextInt(n + 1), lMax = lMin + r.nextInt(n + 1 - lMin);
        if (r.nextInt(10) < 3) { lMin = 0; lMax = n; }
        return new int[]{sMin, sMax, eMin, eMax, lMin, lMax};
    }

    static boolean accepts(Automaton a, int[] w, int s, int e) {
        int q = a.getInitState();
        for (int i = s; i < e; i++) {
            q = a.getAutomaton()[q][w[i]];
            if (q < 0) return false;
        }
        return a.getAcceptingStates().contains(q);
    }

    /** The bounds of the accepted windows within the given bounds, or null if there is none. */
    static int[] bruteForce(Automaton a, int[] w, int[] b) {
        int[] res = null;
        for (int s = b[0]; s <= b[1]; s++) {
            for (int e = Math.max(s, b[2]); e <= Math.min(b[3], w.length); e++) {
                if (e - s < b[4] || e - s > b[5] || !accepts(a, w, s, e)) continue;
                if (res == null) res = new int[]{s, s, e, e, e - s, e - s};
                res[0] = Math.min(res[0], s); res[1] = Math.max(res[1], s);
                res[2] = Math.min(res[2], e); res[3] = Math.max(res[3], e);
                res[4] = Math.min(res[4], e - s); res[5] = Math.max(res[5], e - s);
            }
        }
        return res;
    }

    static int[] boundsOf(CPIntervalVar itv) {
        return new int[]{itv.startMin(), itv.startMax(), itv.endMin(), itv.endMax(), itv.lengthMin(), itv.lengthMax()};
    }

    /** A fresh interval with the given bounds, or null if they are empty for a present interval. */
    static CPIntervalVar interval(CPSolver cp, int n, boolean present, int[] b) {
        CPIntervalVar itv = present ? makeIntervalVar(cp, false, 0, n) : makeIntervalVar(cp, 0, n);
        try {
            itv.setStartMin(b[0]); itv.setStartMax(b[1]);
            itv.setEndMin(b[2]); itv.setEndMax(b[3]);
            itv.setLengthMin(b[4]); itv.setLengthMax(b[5]);
        } catch (InconsistencyException e) {
            return null;
        }
        return itv;
    }

    static Outcome post(CPSolver cp, CPIntervalVar itv, int[] w, Automaton a) {
        try {
            cp.post(new SubstringRegular(w, itv, a));
        } catch (InconsistencyException e) {
            return Outcome.FAILURE;
        }
        return itv.isAbsent() ? Outcome.ABSENT : new Outcome(false, false, boundsOf(itv));
    }

    /** Checks {@code trials} random instances of {@code lang}; returns how many have an accepted window. */
    private int check(Random r, Lang lang, int trials) {
        int withWindow = 0;
        for (int t = 0; t < trials; t++) {
            int[] w = randomWord(r, lang);
            boolean present = r.nextBoolean();
            int[] drawn = randomBounds(r, w.length);
            Automaton a = lang.automaton();

            CPSolver cp = makeSolver();
            CPIntervalVar itv = interval(cp, w.length, present, drawn);
            if (itv == null || itv.isAbsent()) continue;
            int[] bounds = boundsOf(itv); // as tightened by the interval itself
            int[] expected = bruteForce(a, w, bounds);
            String where = lang.name() + " #" + t + " w=" + Arrays.toString(w) + " bounds=" + Arrays.toString(bounds)
                    + (present ? " present" : " optional");

            Outcome outcome = post(cp, itv, w, a);
            if (expected == null) {
                assertEquals(present ? Outcome.FAILURE : Outcome.ABSENT, outcome, where);
            } else {
                withWindow++;
                assertFalse(outcome.failed() || outcome.absent(), where);
                assertArrayEquals(expected, outcome.bounds(), where);
            }
        }
        return withWindow;
    }

    @Test
    public void exactFilteringIsBoundsConsistentOnThePaperAutomata() {
        Random r = new Random(20261124);
        for (Lang lang : paperLanguages()) {
            int withWindow = check(r, lang, 5000);
            assertTrue(withWindow > 50, lang.name() + ": only " + withWindow + " instances with a window");
        }
    }

    @Test
    public void exactFilteringIsBoundsConsistentOnRandomAutomata() {
        Random r = new Random(1124);
        int withWindow = 0;
        for (int t = 0; t < 400; t++) withWindow += check(r, randomLanguage(r), 50);
        assertTrue(withWindow > 2000, "only " + withWindow + " instances with a window");
    }

    /**
     * W = 1^9 and the runs of length 4 + 5k, with start in [0, 5], end in [4, 9] and length in [4, 5]:
     * every accepted window that fits has length 4 (length 9 is too long). The runs from the starts 0
     * and 5 meet at node (5, q0); the former filtering, which kept only the shortest and longest length
     * at each node, gave the accepting node (9, q4) the lengths [4, 9] and kept length 5.
     */
    @Test
    public void lengthsOfRunsThatMeetAreNotMerged() {
        int[] w = new int[9];
        Arrays.fill(w, 1);
        int[] b = {0, 5, 4, 9, 4, 5};
        CPSolver cp = makeSolver();
        assertArrayEquals(new int[]{0, 5, 4, 9, 4, 4}, post(cp, interval(cp, 9, true, b), w, fourPlusFiveK()).bounds());
    }

    /** W = 1^12, start in [0, 5], end in [6, 12], length in [6, 7]: no window, detected in one call. */
    @Test
    public void noWindowOfAnAllowedLength() {
        int[] w = new int[12];
        Arrays.fill(w, 1);
        int[] b = {0, 5, 6, 12, 6, 7};
        CPSolver cp = makeSolver();
        assertEquals(Outcome.ABSENT, post(cp, interval(cp, 12, false, b), w, fourPlusFiveK()));
        CPSolver cp2 = makeSolver();
        assertEquals(Outcome.FAILURE, post(cp2, interval(cp2, 12, true, b), w, fourPlusFiveK()));
    }
}
