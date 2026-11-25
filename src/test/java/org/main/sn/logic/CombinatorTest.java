package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Event;
import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.main.sn.dsl.Factory.*;

/**
 * AND, OR and NOT on groups, checked by equivalences between queries. One name stands for one
 * player wherever it appears, whichever OR branch holds it.
 */
public class CombinatorTest {

    private static final List<String> GAMES = List.of("SNGS-060", "SNGS-061");

    /** Each answer as the atomic events it is made of: their kind, interval and players, in order. */
    private static Set<List<String>> atoms(SelectExpr query) {
        List<Result> results = query.search();
        Set<List<String>> answers = new HashSet<>();
        for (Result r : results) {
            List<String> atoms = new ArrayList<>();
            for (Result.ResultEvent e : r.events) addAtoms(e, atoms);
            answers.add(atoms);
        }
        assertEquals(results.size(), answers.size(), "duplicate answers");
        Factory.reset();
        return answers;
    }

    private static void addAtoms(Result.ResultEvent e, List<String> atoms) {
        if (e instanceof Result.GroupEvent g) {
            for (Result.ResultEvent child : g.children) addAtoms(child, atoms);
            return;
        }
        String players = switch (e) {
            case Result.PositionEvent p -> p.playerIds.toString();
            case Result.PossessionEvent p -> p.playerIds.toString();
            case Result.PassEvent p -> p.passerId + ">" + p.receiverId;
            case Result.PlayerMoveEvent m -> String.valueOf(m.playerId);
            case Result.TeamMoveEvent m -> m.playerIds.toString();
            default -> "";
        };
        atoms.add(e.type + (e.negated ? " NOT" : "") + " " + players + " [" + e.interval.start + "," + e.interval.end + ")");
    }

    /** The intervals of the first step of each answer. */
    private static Set<List<Integer>> firstIntervals(SelectExpr query) {
        Set<List<Integer>> intervals = new HashSet<>();
        for (Result r : query.search()) {
            Result.ResultEvent e = r.events.getFirst();
            intervals.add(List.of(e.interval.start, e.interval.end));
        }
        Factory.reset();
        return intervals;
    }

    @Test
    public void notOfAGroupFollowsDeMorgan() throws Exception {
        int answers = 0;
        for (String G : GAMES) {
            Factory.reset();
            Set<List<String>> notAnd = atoms(SELECT(NOT(AND(POSITION(PLAYER("p", "left"), 1, 2), POSSESSION(PLAYER("q", "right"))))).FROM(G));
            Set<List<String>> orOfNots = atoms(SELECT(OR(NOT(POSITION(PLAYER("p", "left"), 1, 2)), NOT(POSSESSION(PLAYER("q", "right"))))).FROM(G));
            assertEquals(orOfNots, notAnd, "NOT(AND) " + G);

            Set<List<String>> notOr = atoms(SELECT(NOT(OR(POSITION(BALL(), 1, 2), POSSESSION(PLAYER("q", 7))))).FROM(G));
            Set<List<String>> andOfNots = atoms(SELECT(AND(NOT(POSITION(BALL(), 1, 2)), NOT(POSSESSION(PLAYER("q", 7))))).FROM(G));
            assertEquals(andOfNots, notOr, "NOT(OR) " + G);
            answers += notAnd.size() + notOr.size();
        }
        assertTrue(answers > 0);
    }

    @Test
    public void neitherIsTheMaximalRunOfBothNegations() throws Exception {
        // the ball in neither zones 1-2 nor zones 3-4 is the ball out of zones 1 to 4
        int answers = 0;
        for (String G : GAMES) {
            Factory.reset();
            Set<List<Integer>> neither = firstIntervals(SELECT(NOT(OR(POSITION(BALL(), 1, 2), POSITION(BALL(), 3, 4)))).FROM(G));
            Set<List<Integer>> outside = firstIntervals(SELECT(NOT(POSITION(BALL(), 1, 2, 3, 4))).FROM(G));
            assertEquals(outside, neither, G);
            answers += neither.size();
        }
        assertTrue(answers > 0);
    }

    @Test
    public void doubleNegationCancels() throws Exception {
        for (String G : GAMES) {
            Factory.reset();
            Set<List<String>> plain = atoms(SELECT(POSITION(PLAYER("p", "left"), 1, 2)).FROM(G));
            assertFalse(plain.isEmpty(), G);
            assertEquals(plain, atoms(SELECT(NOT(NOT(POSITION(PLAYER("p", "left"), 1, 2)))).FROM(G)), G);
            // inside a group: NOT(AND(NOT(a), b)) is OR(a, NOT(b))
            assertEquals(atoms(SELECT(OR(POSITION(PLAYER("p", "left"), 1, 2), NOT(POSITION(BALL(), 3, 4)))).FROM(G)),
                    atoms(SELECT(NOT(AND(NOT(POSITION(PLAYER("p", "left"), 1, 2)), POSITION(BALL(), 3, 4)))).FROM(G)), G);
        }
    }

    @Test
    public void aWhereOnANegatedGroupStaysOnTheGroupReplacingIt() throws Exception {
        int dropped = 0;
        for (String G : GAMES) {
            Factory.reset();
            Set<List<String>> all = atoms(SELECT(OR(NOT(POSITION(BALL(), 1, 2)), NOT(POSITION(BALL(), 3, 4)))).FROM(G));
            Set<List<String>> rewritten = atoms(SELECT(OR(NOT(POSITION(BALL(), 1, 2)), NOT(POSITION(BALL(), 3, 4)))
                    .WHERE(MAXDURATION(50))).FROM(G));
            Set<List<String>> negated = atoms(SELECT(NOT(AND(POSITION(BALL(), 1, 2), POSITION(BALL(), 3, 4)).WHERE(MAXDURATION(50)))).FROM(G));
            assertEquals(rewritten, negated, G);
            dropped += all.size() - rewritten.size();
        }
        assertTrue(dropped > 0);
    }

    /** The answers of SELECT(steps).FROM(game), as atomic events. */
    private static Set<List<String>> atomsOf(String game, Event... steps) {
        Factory.reset();
        try {
            return atoms(SELECT(steps).FROM(game));
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @SafeVarargs
    private static Set<List<String>> union(Set<List<String>>... sets) {
        Set<List<String>> all = new HashSet<>();
        for (Set<List<String>> s : sets) {
            for (List<String> answer : s) assertTrue(all.add(answer), "the same answer from two branches: " + answer);
        }
        return all;
    }

    // members whose answers never coincide: the ball in two disjoint sets of zones, and a player
    private static Event a() { return POSITION(BALL(), 1, 2); }
    private static Event b() { return POSITION(BALL(), 3, 4); }
    private static Event c() { return POSITION(PLAYER("p", "left"), 1, 2, 3, 4); }
    private static Event d() { return POSITION(BALL(), 13, 14); }

    @Test
    public void nestedGroupsMeanWhatTheirFlatFormsMean() throws Exception {
        int answers = 0;
        for (String G : GAMES) {
            // AND(OR(a, b), c) is AND(a, c) or AND(b, c)
            Set<List<String>> andOfOr = atomsOf(G, AND(OR(a(), b()), c()));
            assertEquals(union(atomsOf(G, AND(a(), c())), atomsOf(G, AND(b(), c()))), andOfOr, "AND(OR) " + G);
            // OR(AND(a, c), d) is AND(a, c) or d
            Set<List<String>> orOfAnd = atomsOf(G, OR(AND(a(), c()), d()));
            assertEquals(union(atomsOf(G, AND(a(), c())), atomsOf(G, d())), orOfAnd, "OR(AND) " + G);
            // groups of the same kind flatten
            Set<List<String>> andOfAnd = atomsOf(G, AND(AND(a(), c()), POSITION(PLAYER("r", "right"), 1, 2, 3, 4)));
            assertEquals(atomsOf(G, AND(a(), c(), POSITION(PLAYER("r", "right"), 1, 2, 3, 4))), andOfAnd, "AND(AND) " + G);
            Set<List<String>> orOfOr = atomsOf(G, OR(OR(a(), b()), d()));
            assertEquals(atomsOf(G, OR(a(), b(), d())), orOfOr, "OR(OR) " + G);
            assertFalse(andOfAnd.isEmpty() || orOfOr.isEmpty(), G);
            // three levels, with an OR that may be absent: OR(AND(OR(a, b), c), d)
            assertEquals(union(atomsOf(G, AND(a(), c())), atomsOf(G, AND(b(), c())), atomsOf(G, d())),
                    atomsOf(G, OR(AND(OR(a(), b()), c()), d())), "OR(AND(OR)) " + G);
            answers += andOfOr.size() + orOfAnd.size();
        }
        assertTrue(answers > 0);
    }

    @Test
    public void nestedGroupsInASequence() throws Exception {
        int answers = 0;
        for (String G : GAMES) {
            Set<List<String>> nested = atomsOf(G, d(), AND(OR(a(), b()), c()));
            assertEquals(union(atomsOf(G, d(), AND(a(), c())), atomsOf(G, d(), AND(b(), c()))), nested, G);
            answers += nested.size();
        }
        assertTrue(answers > 0);
    }

    @Test
    public void aNameInNestedBranchesIsOnePlayer() throws Exception {
        int answers = 0;
        for (String G : GAMES) {
            Set<List<String>> nested = atomsOf(G, OR(AND(POSITION(PLAYER("p", "left"), 1), POSITION(BALL(), 1, 2, 3, 4)),
                    POSITION(PLAYER("p", "left"), 2)));
            assertEquals(union(atomsOf(G, AND(POSITION(PLAYER("p", "left"), 1), POSITION(BALL(), 1, 2, 3, 4))),
                    atomsOf(G, POSITION(PLAYER("p", "left"), 2))), nested, G);
            answers += nested.size();
        }
        assertTrue(answers > 0);
    }

    @Test
    public void modifiersOnNestedGroupsReachEveryAtomicEvent() throws Exception {
        int spatial = 0, minrange = 0;
        for (String G : GAMES) {
            Set<List<String>> shaped = atomsOf(G, OR(AND(a(), c()), b()).WHERE(RADIUS(0, 0, 25)));
            assertEquals(atomsOf(G, OR(AND(a().WHERE(RADIUS(0, 0, 25)), c().WHERE(RADIUS(0, 0, 25))), b().WHERE(RADIUS(0, 0, 25)))),
                    shaped, "spatial " + G);
            Set<List<String>> minimal = atomsOf(G, AND(OR(PLAYER("m", "left").MOVETO(1, 4), PLAYER("m", "left").MOVETO(4, 1)),
                    POSITION(BALL(), 1, 2, 3, 4)).MINRANGE());
            assertEquals(atomsOf(G, AND(OR(PLAYER("m", "left").MOVETO(1, 4).MINRANGE(), PLAYER("m", "left").MOVETO(4, 1).MINRANGE()),
                    POSITION(BALL(), 1, 2, 3, 4).MINRANGE())), minimal, "MINRANGE " + G);
            spatial += shaped.size();
            minrange += minimal.size();
        }
        assertTrue(spatial > 0 && minrange > 0);
    }

    @Test
    public void deMorganGoesThroughNestedGroups() throws Exception {
        int answers = 0;
        for (String G : GAMES) {
            // NOT(AND(OR(a, b), c)) is OR(AND(NOT(a), NOT(b)), NOT(c))
            Set<List<String>> negated = atomsOf(G, NOT(AND(OR(a(), b()), c())));
            assertEquals(atomsOf(G, OR(AND(NOT(a()), NOT(b())), NOT(c()))), negated, G);
            answers += negated.size();
        }
        assertTrue(answers > 0);
    }

    @Test
    public void aNameInTwoOrBranchesKeepsBothBranches() throws Exception {
        int fromSecondBranch = 0;
        for (String G : GAMES) {
            Factory.reset();
            Set<List<String>> zone1 = atoms(SELECT(POSITION(PLAYER("p", "left"), 1)).FROM(G));
            Set<List<String>> zone2 = atoms(SELECT(POSITION(PLAYER("p", "left"), 2)).FROM(G));
            Set<List<String>> expected = new HashSet<>(zone1);
            expected.addAll(zone2);
            fromSecondBranch += zone2.size();
            assertEquals(expected, atoms(SELECT(OR(POSITION(PLAYER("p", "left"), 1), POSITION(PLAYER("p", "left"), 2))).FROM(G)), G);
        }
        assertTrue(fromSecondBranch > 0);
    }
}
