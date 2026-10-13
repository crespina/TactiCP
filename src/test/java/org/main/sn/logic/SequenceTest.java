package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.main.sn.dsl.Factory.*;
import static org.maxicp.cp.CPFactory.makeSolver;

/**
 * The steps of a SELECT follow one another whatever their kind: each step starts at the earliest on
 * the last frame of the previous one. An AND step is placed by its intersection, so its children may
 * start earlier, and an OR step by its chosen branch. Checked against direct scans of the ball's
 * zone sequence: a ball POSITION is a maximal stay, and a top-level step lasts at least one frame.
 */
public class SequenceTest {

    private static final List<String> GAMES = List.of("SNGS-060", "SNGS-061");

    private static GameStateReconstructionInstance game(String name) throws IOException {
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(BALL().MOVETO(1, 2)).FROM(name).matches.getFirst();
        Factory.reset();
        return g;
    }

    private static int[] ballZones(GameStateReconstructionInstance g) {
        return new Position(makeSolver(), g).position[g.ball_idx];
    }

    /** Maximal stays [s, e) of {@code Z} in the zones, from frame 1, at least {@code minLength} frames long. */
    private static List<int[]> stays(int[] Z, Set<Integer> zones, int minLength) {
        List<int[]> stays = new ArrayList<>();
        for (int s = 1, e; s < Z.length; s = e) {
            e = s + 1;
            boolean in = zones.contains(Z[s]);
            while (e < Z.length && zones.contains(Z[e]) == in) e++;
            if (in && e - s >= minLength) stays.add(new int[]{s, e});
        }
        return stays;
    }

    /** Each answer as the bounds of its steps in order, a group followed by those of its children. */
    private static Set<List<Integer>> run(SelectExpr query) {
        List<Result> results = query.search();
        Set<List<Integer>> answers = new HashSet<>();
        for (Result r : results) {
            List<Integer> bounds = new ArrayList<>();
            for (Result.ResultEvent e : r.events) {
                bounds.add(e.interval.start);
                bounds.add(e.interval.end);
                if (e instanceof Result.GroupEvent group) {
                    for (Result.ResultEvent child : group.children) {
                        bounds.add(child.interval.start);
                        bounds.add(child.interval.end);
                    }
                }
            }
            answers.add(bounds);
        }
        assertEquals(results.size(), answers.size(), "duplicate answers");
        Factory.reset();
        return answers;
    }

    @Test
    public void anOrStepFollowsThePreviousStep() throws IOException {
        int dropped = 0, kept = 0;
        for (String G : GAMES) {
            int[] Z = ballZones(game(G));
            Set<List<Integer>> expected = new HashSet<>();
            for (int[] a : stays(Z, Set.of(1, 2, 3, 4), 1)) {
                for (int zone : new int[]{13, 14}) {
                    for (int[] b : stays(Z, Set.of(zone), 1)) {
                        if (b[0] >= a[1] - 1) expected.add(List.of(a[0], a[1], b[0], b[1], b[0], b[1]));
                        else dropped++;
                    }
                }
            }
            kept += expected.size();
            assertEquals(expected, run(SELECT(POSITION(BALL(), 1, 2, 3, 4),
                    OR(POSITION(BALL(), 13), POSITION(BALL(), 14))).FROM(G)), G);
        }
        // the order is what tells the answers apart from all the pairs of stays
        assertTrue(kept > 0);
        assertTrue(dropped > 0);
    }

    @Test
    public void anAndStepFollowsThePreviousStepByItsIntersection() throws IOException {
        // A player's stay in zone 1, then a stay in zone 2 during a stay in the central zones, which
        // may have begun before the stay in zone 1 ended. Checked for every player with such stays.
        int kept = 0, dropped = 0, childStartsEarlier = 0;
        for (String G : GAMES) {
            GameStateReconstructionInstance g = game(G);
            int[][] zones = new Position(makeSolver(), g).position;
            for (int id = 1; id < g.teams.length; id++) {
                if (g.teams[id] != 0 && g.teams[id] != 1) continue;
                int[] Z = zones[id];
                Set<List<Integer>> expected = new HashSet<>();
                int pairs = 0;
                for (int[] a : stays(Z, Set.of(1), 1)) {
                    for (int[] x : stays(Z, Set.of(1, 2, 3, 4), 1)) {
                        for (int[] y : stays(Z, Set.of(2), 1)) {
                            int s = Math.max(x[0], y[0]), e = Math.min(x[1], y[1]);
                            if (e - s < 1) continue;
                            pairs++;
                            if (s >= a[1] - 1) {
                                expected.add(List.of(a[0], a[1], s, e, x[0], x[1], y[0], y[1]));
                                if (x[0] < a[1] - 1) childStartsEarlier++;
                            } else {
                                dropped++;
                            }
                        }
                    }
                }
                if (pairs == 0) continue;
                kept += expected.size();
                assertEquals(expected, run(SELECT(POSITION(PLAYER("p", id), 1),
                        AND(POSITION(PLAYER("p", id), 1, 2, 3, 4), POSITION(PLAYER("p", id), 2))).FROM(G)), G + " player " + id);
            }
        }
        assertTrue(kept > 0);
        assertTrue(dropped > 0);
        // a child that starts before the previous step ends does not stop the AND from following it
        assertTrue(childStartsEarlier > 0);
    }
}
