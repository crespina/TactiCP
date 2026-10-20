package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;

import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.main.sn.dsl.Factory.*;
import static org.maxicp.cp.CPFactory.makeSolver;

/**
 * Ball and Player POSITION checked against a direct scan of the zone sequences. In SNGS-127 the
 * ball moves between zones 10 and 11 only, and eight players stay in zones 1 to 4 for the whole
 * clip. No stay covers the dummy frame 0, where nothing is tracked: a stay, negated or not, that
 * reaches the start of the clip starts on frame 1. Every expected interval is at least one frame
 * long, as for any top-level event.
 */
public class PositionTest {

    private static final String GAME = "SNGS-127";

    record Stay(int who, int start, int end) {}

    private static GameStateReconstructionInstance game() throws IOException {
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(POSITION(BALL(), 1)).FROM(GAME).matches.getFirst();
        Factory.reset();
        return g;
    }

    private static int[][] zones(GameStateReconstructionInstance g) {
        return new Position(makeSolver(), g).position;
    }

    /** Maximal runs [s, e) of {@code Z} in (or, negated, outside) {@code zones}, from frame 1. */
    private static Set<Stay> stays(int[] Z, int who, Set<Integer> zones, boolean negated) {
        Set<Stay> stays = new HashSet<>();
        for (int s = 1, e = 2; s < Z.length; s = e, e = s + 1) {
            boolean in = zones.contains(Z[s]) != negated;
            while (e < Z.length && (zones.contains(Z[e]) != negated) == in) e++;
            if (in) stays.add(new Stay(who, s, e));
        }
        return stays;
    }

    /** The answers of a single-event POSITION query; the ball is reported as -1. */
    private static Set<Stay> answers(SelectExpr query) {
        List<Result> results = query.search();
        Set<Stay> stays = new HashSet<>();
        for (Result r : results) {
            Result.PositionEvent e = (Result.PositionEvent) r.events.getFirst();
            stays.add(new Stay(e.playerIds.isEmpty() ? -1 : e.playerIds.getFirst(), e.interval.start, e.interval.end));
        }
        assertEquals(results.size(), stays.size(), "duplicate answers");
        return stays;
    }

    @Test
    public void negatedBallStayCoversTheWholeClip() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] ball = zones(g)[g.ball_idx];
        assertTrue(Arrays.stream(ball).noneMatch(z -> z == 13));

        assertEquals(Set.of(new Stay(-1, 1, ball.length)), answers(SELECT(NOT(POSITION(BALL(), 13))).FROM(GAME)));
    }

    @Test
    public void ballStaysMatchTheScan() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] ball = zones(g)[g.ball_idx];
        for (int[] zs : List.of(new int[]{10}, new int[]{11}, new int[]{10, 11})) {
            Set<Integer> set = new HashSet<>();
            for (int z : zs) set.add(z);
            Factory.reset();
            assertEquals(stays(ball, -1, set, false), answers(SELECT(POSITION(BALL(), zs)).FROM(GAME)));
            Factory.reset();
            assertEquals(stays(ball, -1, set, true), answers(SELECT(NOT(POSITION(BALL(), zs))).FROM(GAME)));
        }
    }

    @Test
    public void negatedPlayerStayCoversTheWholeClip() throws IOException {
        GameStateReconstructionInstance g = game();
        int[][] Z = zones(g);
        int away = -1;
        for (int i = 0; i < g.teams.length && away < 0; i++) {
            if (g.teams[i] == 0 && Arrays.stream(Z[i]).noneMatch(z -> z == 13)) away = i;
        }
        assertTrue(away >= 0);

        assertEquals(Set.of(new Stay(away, 1, Z[away].length)),
                answers(SELECT(NOT(POSITION(PLAYER("away", away), 13))).FROM(GAME)));
    }

    @Test
    public void playerStayCoversTheWholeClip() throws IOException {
        GameStateReconstructionInstance g = game();
        int[][] Z = zones(g);
        Set<Integer> central = Set.of(1, 2, 3, 4);
        int player = -1;
        for (int i = 0; i < g.teams.length && player < 0; i++) {
            final int p = i;
            if ((g.teams[p] == 0 || g.teams[p] == 1)
                    && IntStream.range(1, Z[p].length).allMatch(f -> central.contains(Z[p][f]))) player = p;
        }
        assertTrue(player >= 0);

        // from the first real frame: the dummy frame 0 is in no zone
        assertEquals(Set.of(new Stay(player, 1, Z[player].length)),
                answers(SELECT(POSITION(PLAYER("central", player), 1, 2, 3, 4)).FROM(GAME)));
    }

    @Test
    public void playerStaysMatchTheScan() throws IOException {
        GameStateReconstructionInstance g = game();
        int[][] Z = zones(g);
        Set<Integer> central = Set.of(1, 2, 3, 4);
        Set<Stay> in = new HashSet<>(), out = new HashSet<>();
        for (int i = 0; i < g.teams.length; i++) {
            if (g.teams[i] != 0) continue;
            in.addAll(stays(Z[i], i, central, false));
            out.addAll(stays(Z[i], i, central, true));
        }

        assertEquals(in, answers(SELECT(POSITION(PLAYER("pl", "left"), 1, 2, 3, 4)).FROM(GAME)));
        Factory.reset();
        assertEquals(out, answers(SELECT(NOT(POSITION(PLAYER("pl", "left"), 1, 2, 3, 4))).FROM(GAME)));
    }
}
