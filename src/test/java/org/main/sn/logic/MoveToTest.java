package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Event;
import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.main.sn.dsl.Factory.*;
import static org.maxicp.cp.CPFactory.makeSolver;

/**
 * Ball and Player MOVE_TO checked against a direct scan of the zone sequences. A move from zs to
 * ze is a maximal run of zs followed by a maximal run of ze, with only other symbols in between,
 * undetected frames (0) included. The extended interval spans both runs; the MINRANGE one runs
 * from the last frame of the zs run to the first frame of the ze run. Both modes find the same
 * moves. In SNGS-025 the ball and several left players go undetected during some of their moves.
 * In SNGS-021 a left player's only move spans the whole clip: in the start zone from the first
 * frame, in the end zone until the last one.
 */
public class MoveToTest {

    private static final String GAME = "SNGS-025";

    record Move(int who, int start, int end) {}

    record Run(int symbol, int start, int end) {}

    private static GameStateReconstructionInstance game() throws IOException {
        return game(GAME);
    }

    private static GameStateReconstructionInstance game(String name) throws IOException {
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(BALL().MOVETO(1, 2)).FROM(name).matches.getFirst();
        Factory.reset();
        return g;
    }

    private static int[][] zones(GameStateReconstructionInstance g) {
        return new Position(makeSolver(), g).position;
    }

    /** Maximal runs of a same symbol, from frame 1: frame 0 is a dummy. */
    private static List<Run> runs(int[] Z) {
        List<Run> runs = new ArrayList<>();
        for (int s = 1, e = 2; s < Z.length; s = e, e = s + 1) {
            while (e < Z.length && Z[e] == Z[s]) e++;
            runs.add(new Run(Z[s], s, e));
        }
        return runs;
    }

    /** Every move from zs to ze in Z; with gapOnly, only those with an undetected frame in between. */
    private static Set<Move> moves(int[] Z, int who, int zs, int ze, boolean minrange, boolean gapOnly) {
        List<Run> runs = runs(Z);
        Set<Move> moves = new HashSet<>();
        for (int i = 0; i < runs.size(); i++) {
            if (runs.get(i).symbol() != zs) continue;
            boolean gap = false;
            for (int j = i + 1; j < runs.size() && runs.get(j).symbol() != zs; j++) {
                if (runs.get(j).symbol() == 0) gap = true;
                if (runs.get(j).symbol() == ze) {
                    Run a = runs.get(i), b = runs.get(j);
                    if (!gapOnly || gap) {
                        moves.add(minrange ? new Move(who, a.end() - 1, b.start() + 1) : new Move(who, a.start(), b.end()));
                    }
                    break;
                }
            }
        }
        return moves;
    }

    /** The answers of a single-event MOVE_TO query; the ball is reported as -1. */
    private static Set<Move> answers(SelectExpr query) {
        List<Result> results = query.search();
        Set<Move> moves = new HashSet<>();
        for (Result r : results) {
            Result.ResultEvent e = r.events.getFirst();
            int who = e instanceof Result.PlayerMoveEvent pm ? pm.playerId : -1;
            moves.add(new Move(who, e.interval.start, e.interval.end));
        }
        assertEquals(results.size(), moves.size(), "duplicate answers");
        return moves;
    }

    @Test
    public void ballMovesMatchTheScan() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] ball = zones(g)[g.ball_idx];
        Set<Integer> visited = new TreeSet<>();
        for (int f = 1; f < ball.length; f++) if (ball[f] != 0) visited.add(ball[f]);

        int throughGap = 0;
        for (int zs : visited) {
            for (int ze : visited) {
                if (zs == ze) continue;
                for (boolean minrange : new boolean[]{false, true}) {
                    Factory.reset();
                    Event e = BALL().MOVETO(zs, ze);
                    if (minrange) e = e.MINRANGE();
                    assertEquals(moves(ball, -1, zs, ze, minrange, false), answers(SELECT(e).FROM(GAME)),
                            zs + " -> " + ze + (minrange ? " MINRANGE" : ""));
                }
                throughGap += moves(ball, -1, zs, ze, true, true).size();
            }
        }
        // a move during which the ball is lost for a while: the case being tested
        assertTrue(throughGap > 0);
    }

    @Test
    public void playerMovesMatchTheScan() throws IOException {
        GameStateReconstructionInstance g = game();
        int[][] Z = zones(g);
        List<Integer> left = new ArrayList<>();
        for (int i = 1; i < g.teams.length; i++) if (g.teams[i] == 0) left.add(i);

        // the move made by the most left players through an undetected frame
        int bestZs = -1, bestZe = -1, best = 0;
        for (int zs = 1; zs <= 14; zs++) {
            for (int ze = 1; ze <= 14; ze++) {
                if (zs == ze) continue;
                int count = 0;
                for (int c : left) count += moves(Z[c], c, zs, ze, true, true).size();
                if (count > best) { best = count; bestZs = zs; bestZe = ze; }
            }
        }
        assertTrue(best > 0);

        for (boolean minrange : new boolean[]{false, true}) {
            Set<Move> expected = new HashSet<>();
            for (int c : left) expected.addAll(moves(Z[c], c, bestZs, bestZe, minrange, false));
            Factory.reset();
            Event e = PLAYER("mover", "left").MOVETO(bestZs, bestZe);
            if (minrange) e = e.MINRANGE();
            assertEquals(expected, answers(SELECT(e).FROM(GAME)), bestZs + " -> " + bestZe + (minrange ? " MINRANGE" : ""));
        }
    }

    @Test
    public void moveSpanningTheWholeClip() throws IOException {
        GameStateReconstructionInstance g = game("SNGS-021");
        int[][] Z = zones(g);
        for (int c = 1; c < g.teams.length; c++) {
            if (g.teams[c] != 0) continue;
            for (int zs = 1; zs <= 14; zs++) {
                for (int ze = 1; ze <= 14; ze++) {
                    if (zs == ze) continue;
                    Set<Move> expected = moves(Z[c], c, zs, ze, false, false);
                    if (!expected.contains(new Move(c, 1, Z[c].length))) continue;
                    Factory.reset();
                    assertEquals(expected, answers(SELECT(PLAYER("mover", c).MOVETO(zs, ze)).FROM("SNGS-021")));
                    return;
                }
            }
        }
        fail("no move spans the whole clip in SNGS-021");
    }
}
