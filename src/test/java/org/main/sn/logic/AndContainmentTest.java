package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Event;
import org.main.sn.dsl.Factory;

import java.io.IOException;
import java.util.*;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.main.sn.dsl.Factory.*;
import static org.maxicp.cp.CPFactory.makeSolver;

/**
 * In an AND, states are cut and transitions stay whole: a pass or a move must lie inside every
 * state of the AND, and the AND reports the transition itself. A transition that only overlaps a
 * state is no answer. Checked against a scan of the zone sequences, then by equivalences showing
 * that the rule reads the atomic events through nested groups.
 */
public class AndContainmentTest {

    private static final List<String> GAMES = List.of("SNGS-060", "SNGS-061");
    private static final int[] CENTRE = {1, 2, 3, 4};

    record Span(int start, int end) {}

    record Pair(Span move, Span stay) {}

    private static GameStateReconstructionInstance game(String name) throws IOException {
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(BALL().MOVETO(1, 2)).FROM(name).games().getFirst();
        Factory.reset();
        return g;
    }

    /** MINRANGE moves of Z from zs to ze: last frame in zs to first frame in ze, frame 0 is a dummy. */
    private static List<Span> minimalMoves(int[] Z, int zs, int ze) {
        List<Span> moves = new ArrayList<>();
        for (int f = 1; f < Z.length; f++) {
            if (Z[f] != zs || (f + 1 < Z.length && Z[f + 1] == zs)) continue;
            for (int g = f + 1; g < Z.length && Z[g] != zs; g++) {
                if (Z[g] == ze) {
                    moves.add(new Span(f, g + 1));
                    break;
                }
            }
        }
        return moves;
    }

    /** Maximal runs of the frames where Z is in zones. */
    private static List<Span> stays(int[] Z, int[] zones) {
        Set<Integer> in = new HashSet<>();
        for (int z : zones) in.add(z);
        List<Span> stays = new ArrayList<>();
        for (int s = 1, e; s < Z.length; s = e) {
            e = s + 1;
            if (!in.contains(Z[s])) continue;
            while (e < Z.length && in.contains(Z[e])) e++;
            stays.add(new Span(s, e));
        }
        return stays;
    }

    private static Set<Pair> inside(List<Span> moves, List<Span> stays) {
        Set<Pair> pairs = new HashSet<>();
        for (Span m : moves) for (Span s : stays) if (s.start <= m.start && m.end <= s.end) pairs.add(new Pair(m, s));
        return pairs;
    }

    private static int overlappingOnly(List<Span> moves, List<Span> stays) {
        int count = 0;
        for (Span m : moves) {
            for (Span s : stays) {
                boolean overlap = Math.min(m.end, s.end) > Math.max(m.start, s.start);
                if (overlap && !(s.start <= m.start && m.end <= s.end)) count++;
            }
        }
        return count;
    }

    @Test
    public void aMoveIsKeptOnlyWhenItLiesInsideTheStateOfItsAnd() throws IOException {
        // the ball move and the player of the state are chosen so that some moves lie inside one of
        // the player's stays in the centre and others only overlap one: the case that changed
        for (String G : GAMES) {
            GameStateReconstructionInstance g = game(G);
            int[][] Z = new Position(makeSolver(), g).position;
            int[] ball = Z[g.ball_idx];
            for (int zs : CENTRE) {
                for (int ze : CENTRE) {
                    if (zs == ze) continue;
                    List<Span> moves = minimalMoves(ball, zs, ze);
                    for (int p = 1; p < g.teams.length; p++) {
                        if (p == g.ball_idx || g.teams[p] < 0) continue;
                        List<Span> stays = stays(Z[p], CENTRE);
                        Set<Pair> expected = inside(moves, stays);
                        if (expected.isEmpty() || overlappingOnly(moves, stays) == 0) continue;

                        Factory.reset();
                        List<Result> results = SELECT(AND(BALL().MOVETO(zs, ze).MINRANGE(),
                                POSITION(PLAYER("p", p), CENTRE))).FROM(G).search();
                        Factory.reset();
                        Set<Pair> found = new HashSet<>();
                        for (Result r : results) {
                            Result.GroupEvent and = (Result.GroupEvent) r.events.getFirst();
                            Result.ResultEvent move = and.children.get(0);
                            Result.ResultEvent stay = and.children.get(1);
                            // the AND reports the move itself
                            assertEquals(move.interval.start, and.interval.start);
                            assertEquals(move.interval.end, and.interval.end);
                            found.add(new Pair(new Span(move.interval.start, move.interval.end),
                                    new Span(stay.interval.start, stay.interval.end)));
                        }
                        assertEquals(results.size(), found.size(), "duplicate answers");
                        assertEquals(expected, found, G + " ball " + zs + " -> " + ze + ", player " + p);
                        return;
                    }
                }
            }
        }
        fail("no ball move both inside and only overlapping a player's stay in the centre");
    }

    /**
     * Each answer as its atomic events, sorted (members may be listed in any order): kind, interval
     * and players. The step is built after the reset, so that no name is resolved from an earlier query.
     */
    private static Set<List<String>> atoms(String game, Supplier<Event> step) {
        Factory.reset();
        List<Result> results;
        try {
            results = SELECT(step.get()).FROM(game).search();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        Set<List<String>> answers = new HashSet<>();
        for (Result r : results) {
            List<String> atoms = new ArrayList<>();
            for (Result.ResultEvent e : r.events) addAtoms(e, atoms);
            Collections.sort(atoms);
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
        String players = e instanceof Result.PositionEvent p ? p.playerIds.toString() : "";
        atoms.add(e.type + (e.negated ? " NOT" : "") + " " + players + " [" + e.interval.start + "," + e.interval.end + ")");
    }

    private static Event move1() { return BALL().MOVETO(1, 2).MINRANGE(); }
    private static Event move2() { return BALL().MOVETO(4, 3).MINRANGE(); }
    private static Event centre() { return POSITION(PLAYER("p", "left"), CENTRE); }
    private static Event notInBoxes() { return NOT(POSITION(BALL(), 13, 14)); }

    @Test
    public void containmentDistributesOverOr() {
        int answers = 0;
        for (String G : GAMES) {
            // AND(OR(t1, t2), s) is AND(t1, s) or AND(t2, s): each move inside a stay of p
            Set<List<String>> andOfOr = atoms(G, () -> AND(OR(move1(), move2()), centre()));
            Set<List<String>> union = new HashSet<>(atoms(G, () -> AND(move1(), centre())));
            union.addAll(atoms(G, () -> AND(move2(), centre())));
            assertEquals(union, andOfOr, "AND(OR) " + G);
            assertEquals(atoms(G, () -> OR(AND(move1(), centre()), AND(move2(), centre()))), andOfOr, "OR(AND) " + G);
            answers += andOfOr.size();
        }
        assertTrue(answers > 0);
    }

    @Test
    public void containmentGoesThroughNestedAnds() {
        int answers = 0;
        for (String G : GAMES) {
            // a transition inside an inner AND must also lie inside the states of the outer one
            Set<List<String>> flat = atoms(G, () -> AND(move1(), centre(), notInBoxes()));
            assertEquals(flat, atoms(G, () -> AND(AND(move1(), centre()), notInBoxes())), "inner AND " + G);
            assertEquals(flat, atoms(G, () -> AND(centre(), AND(notInBoxes(), move1()))), "inner AND, reordered " + G);
            answers += flat.size();
        }
        assertTrue(answers > 0);
    }

    @Test
    public void aStateBranchOfAnOrHoldsATransitionOfTheAnd() {
        int answers = 0;
        for (String G : GAMES) {
            // AND(OR(s1, s2), t) is AND(s1, t) or AND(s2, t): the move inside the chosen state
            Set<List<String>> andOfOr = atoms(G, () -> AND(OR(POSITION(PLAYER("p", "left"), 1, 2),
                    POSITION(PLAYER("p", "left"), 3, 4)), move1()));
            Set<List<String>> union = new HashSet<>(atoms(G, () -> AND(POSITION(PLAYER("p", "left"), 1, 2), move1())));
            union.addAll(atoms(G, () -> AND(POSITION(PLAYER("p", "left"), 3, 4), move1())));
            assertEquals(union, andOfOr, "AND(OR of states, move) " + G);
            answers += andOfOr.size();
        }
        assertTrue(answers > 0);
    }
}
