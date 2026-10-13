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
 * Spatial WHERE on ball and player events, checked against direct scans. A WHERE keeps the answers
 * of the event during which its subject (the ball for PASSTO and POSSESSION) is never seen outside
 * the shapes: a frame where the subject is not tracked neither confirms nor breaks it. Positions
 * are read on the frame itself, the last frame of the clip included. Each test draws a circle
 * around the subject during one answer, so that the WHERE keeps at least that answer.
 */
public class SpatialWhereTest {

    record Answer(int who, int start, int end) {}

    record Circle(int x, int y, int r) {}

    private static GameStateReconstructionInstance game(String name) throws IOException {
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(BALL().MOVETO(1, 2)).FROM(name).matches.getFirst();
        Factory.reset();
        return g;
    }

    private static int[][] zones(GameStateReconstructionInstance g) {
        return new Position(makeSolver(), g).position;
    }

    /** The possession sequence the events read: ball-free frames between two touches by one player are that player's. */
    private static int[] possession(GameStateReconstructionInstance g) {
        int[] raw = new Possession(makeSolver(), g, true).result;
        int[] bridged = raw.clone();
        for (int s = 1, e; s < raw.length; s = e) {
            e = s + 1;
            if (raw[s] != 0) continue;
            while (e < raw.length && raw[e] == 0) e++;
            if (s > 1 && e < raw.length && raw[s - 1] == raw[e]) Arrays.fill(bridged, s, e, raw[e]);
        }
        return bridged;
    }

    private static List<Integer> left(GameStateReconstructionInstance g) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i < g.teams.length; i++) if (g.teams[i] == 0) ids.add(i);
        return ids;
    }

    /** Where {@code id} is on frame {@code f}, or null when it is not tracked there. */
    private static int[] at(GameStateReconstructionInstance g, int id, int f) {
        GameStateReconstructionInstance.PlayerInfo p = g.positions.get(f).players.get(id);
        return p == null ? null : new int[]{(int) p.pos().x(), (int) p.pos().y()};
    }

    /** A circle around every position of {@code id} seen during [start, end). */
    private static Circle around(GameStateReconstructionInstance g, int id, int start, int end) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int f = start; f < end; f++) {
            int[] p = at(g, id, f);
            if (p == null) continue;
            minX = Math.min(minX, p[0]); maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]); maxY = Math.max(maxY, p[1]);
        }
        return new Circle((minX + maxX) / 2, (minY + maxY) / 2, (int) Math.ceil(Math.hypot(maxX - minX, maxY - minY) / 2) + 1);
    }

    /** Whether {@code id} is never seen outside the circle during [start, end). */
    private static boolean neverOutside(GameStateReconstructionInstance g, int id, int start, int end, Circle c) {
        for (int f = start; f < end; f++) {
            int[] p = at(g, id, f);
            if (p == null) continue;
            int dx = p[0] - c.x, dy = p[1] - c.y;
            if (dx * dx + dy * dy > c.r * c.r) return false;
        }
        return true;
    }

    private static boolean untrackedDuring(GameStateReconstructionInstance g, int id, int start, int end) {
        for (int f = start; f < end; f++) if (at(g, id, f) == null) return true;
        return false;
    }

    private static Set<Answer> keep(Set<Answer> answers, GameStateReconstructionInstance g, Circle c, int subject) {
        Set<Answer> kept = new HashSet<>();
        for (Answer a : answers) if (neverOutside(g, subject < 0 ? a.who : subject, a.start, a.end, c)) kept.add(a);
        return kept;
    }

    /** The answers of a single-event query; the ball is reported as -1, a pass by its passer. */
    private static Set<Answer> run(SelectExpr query) {
        List<Result> results = query.search();
        Set<Answer> answers = new HashSet<>();
        for (Result r : results) {
            Result.ResultEvent e = r.events.getFirst();
            int who = switch (e) {
                case Result.PositionEvent p -> p.playerIds.isEmpty() ? -1 : p.playerIds.getFirst();
                case Result.PlayerMoveEvent m -> m.playerId;
                case Result.PossessionEvent p -> p.playerIds.getFirst();
                case Result.PassEvent p -> p.passerId;
                default -> -1;
            };
            answers.add(new Answer(who, e.interval.start, e.interval.end));
        }
        assertEquals(results.size(), answers.size(), "duplicate answers");
        Factory.reset();
        return answers;
    }

    /** Maximal runs of {@code Z} in (negated: out of) the zones, from frame 1, at least two frames long. */
    private static Set<Answer> stays(int[] Z, int who, Set<Integer> zones, boolean negated) {
        Set<Answer> stays = new HashSet<>();
        for (int s = 1, e = 2; s < Z.length; s = e, e = s + 1) {
            boolean in = zones.contains(Z[s]) != negated;
            while (e < Z.length && (zones.contains(Z[e]) != negated) == in) e++;
            if (in && e - s >= 2) stays.add(new Answer(who, s, e));
        }
        return stays;
    }

    /** Moves zs to ze of {@code Z}, as in MoveToTest. */
    private static Set<Answer> moves(int[] Z, int who, int zs, int ze, boolean minrange) {
        List<int[]> runs = new ArrayList<>();
        for (int s = 1, e; s < Z.length; s = e) {
            e = s + 1;
            while (e < Z.length && Z[e] == Z[s]) e++;
            runs.add(new int[]{Z[s], s, e});
        }
        Set<Answer> moves = new HashSet<>();
        for (int i = 0; i < runs.size(); i++) {
            if (runs.get(i)[0] != zs) continue;
            for (int j = i + 1; j < runs.size() && runs.get(j)[0] != zs; j++) {
                if (runs.get(j)[0] == ze) {
                    int[] a = runs.get(i), b = runs.get(j);
                    moves.add(minrange ? new Answer(who, a[2] - 1, b[1] + 1) : new Answer(who, a[1], b[2]));
                    break;
                }
            }
        }
        return moves;
    }

    @Test
    public void playerStayInsideTheCircle() throws IOException {
        String G = "SNGS-022";
        GameStateReconstructionInstance g = game(G);
        int[][] Z = zones(g);
        Set<Integer> zs = Set.of(1, 2, 3, 4);
        for (boolean negated : new boolean[]{false, true}) {
            Set<Answer> all = new HashSet<>();
            for (int c : left(g)) all.addAll(stays(Z[c], c, zs, negated));
            Answer one = all.stream().filter(a -> a.end - a.start < 200).findFirst().orElseThrow();
            Circle circle = around(g, one.who, one.start, one.end);
            Set<Answer> expected = keep(all, g, circle, -1);
            assertTrue(expected.contains(one));
            assertTrue(expected.size() < all.size());
            Event e = POSITION(PLAYER("p", "left"), 1, 2, 3, 4);
            if (negated) e = NOT(e);
            assertEquals(expected, run(SELECT(e).FROM(G).WHERE(RADIUS(circle.x, circle.y, circle.r))), negated ? "negated" : "positive");
        }
    }

    @Test
    public void stayInsideTheCircleUntilTheLastFrame() throws IOException {
        // in SNGS-127 player 2 is in zones 1 to 4 on every frame
        String G = "SNGS-127";
        GameStateReconstructionInstance g = game(G);
        Circle circle = around(g, 2, 1, g.n + 1);
        assertEquals(Set.of(new Answer(2, 1, g.n + 1)),
                run(SELECT(POSITION(PLAYER("p", 2), 1, 2, 3, 4)).FROM(G).WHERE(RADIUS(circle.x, circle.y, circle.r))));
    }

    @Test
    public void moveThroughUndetectedFrames() throws IOException {
        // in SNGS-025 the ball and several left players go undetected during some of their moves
        String G = "SNGS-025";
        GameStateReconstructionInstance g = game(G);
        int[][] Z = zones(g);
        for (boolean ball : new boolean[]{true, false}) {
            List<Integer> subjects = ball ? List.of(g.ball_idx) : left(g);
            Answer one = null;
            int zsOne = 0, zeOne = 0;
            search:
            for (int zs = 1; zs <= 14; zs++) {
                for (int ze = 1; ze <= 14; ze++) {
                    if (zs == ze) continue;
                    for (int c : subjects) {
                        for (Answer m : moves(Z[c], ball ? -1 : c, zs, ze, false)) {
                            if (untrackedDuring(g, c, m.start, m.end)) {
                                one = m; zsOne = zs; zeOne = ze;
                                break search;
                            }
                        }
                    }
                }
            }
            assertNotNull(one);
            final int zs = zsOne, ze = zeOne;
            Circle circle = around(g, ball ? g.ball_idx : one.who, one.start, one.end);
            for (boolean minrange : new boolean[]{false, true}) {
                Set<Answer> expected = new HashSet<>();
                for (int c : subjects) {
                    for (Answer m : moves(Z[c], ball ? -1 : c, zs, ze, minrange)) {
                        if (neverOutside(g, c, m.start, m.end, circle)) expected.add(m);
                    }
                }
                assertFalse(expected.isEmpty());
                Event e = ball ? BALL().MOVETO(zs, ze) : PLAYER("mover", "left").MOVETO(zs, ze);
                if (minrange) e = e.MINRANGE();
                assertEquals(expected, run(SELECT(e).FROM(G).WHERE(RADIUS(circle.x, circle.y, circle.r))),
                        (ball ? "ball " : "player ") + zs + " -> " + ze + (minrange ? " MINRANGE" : ""));
            }
        }
    }

    @Test
    public void rectangleWithNegativeSides() throws IOException {
        String G = "SNGS-022";
        GameStateReconstructionInstance g = game(G);
        int[][] Z = zones(g);
        Set<Answer> all = stays(Z[g.ball_idx], -1, Set.of(1, 2, 3, 4), false);
        assertFalse(all.isEmpty());
        Set<Answer> positive = run(SELECT(POSITION(BALL(), 1, 2, 3, 4)).FROM(G).WHERE(RECTANGLE(-40, 25, 80, 50)));
        assertFalse(positive.isEmpty());
        assertEquals(positive, run(SELECT(POSITION(BALL(), 1, 2, 3, 4)).FROM(G).WHERE(RECTANGLE(-40, 25, -80, -50))));
    }

    @Test
    public void possessionAndPassTestTheBall() throws IOException {
        String G = "SNGS-060";
        GameStateReconstructionInstance g = game(G);
        int[] P = possession(g);

        Set<Answer> stays = new HashSet<>();
        for (int s = 1, e = 2; s < P.length; s = e, e = s + 1) {
            while (e < P.length && P[e] == P[s]) e++;
            if (P[s] != 0 && (g.teams[P[s]] == 0 || g.teams[P[s]] == 1) && e - s >= 2) stays.add(new Answer(P[s], s, e));
        }
        Answer one = stays.stream().filter(a -> untrackedDuring(g, g.ball_idx, a.start, a.end)).findFirst().orElseThrow();
        Circle circle = around(g, g.ball_idx, one.start, one.end);
        Set<Answer> expected = keep(stays, g, circle, g.ball_idx);
        assertTrue(expected.contains(one));
        assertEquals(expected, run(SELECT(POSSESSION(PLAYER("holder"))).FROM(G).WHERE(RADIUS(circle.x, circle.y, circle.r))));

        // a pass: a stay, ball-free frames, a stay of a teammate
        List<int[]> runs = new ArrayList<>();
        for (int s = 0, e = 1; s < P.length; s = e, e = s + 1) {
            while (e < P.length && P[e] == P[s]) e++;
            runs.add(new int[]{P[s], s, e});
        }
        Set<Answer> passes = new HashSet<>();
        for (int i = 0; i + 2 < runs.size(); i++) {
            int a = runs.get(i)[0], b = runs.get(i + 2)[0];
            if (a == 0 || runs.get(i + 1)[0] != 0 || b == 0 || b == a || g.teams[a] != g.teams[b]) continue;
            passes.add(new Answer(a, runs.get(i)[1], runs.get(i + 2)[2]));
        }
        Answer pass = passes.stream().findFirst().orElseThrow();
        Circle wide = around(g, g.ball_idx, pass.start, pass.end);
        Set<Answer> expectedPasses = keep(passes, g, wide, g.ball_idx);
        assertTrue(expectedPasses.contains(pass));
        assertEquals(expectedPasses, run(SELECT(PLAYER("a").PASSTO(PLAYER("b"))).FROM(G).WHERE(RADIUS(wide.x, wide.y, wide.r))));
    }

    @Test
    public void twoNamesWithOneNumberAreOnePlayer() throws IOException {
        // in SNGS-127 player 1 has stays in zone 1
        String G = "SNGS-127";
        Set<Answer> alone = run(SELECT(POSITION(PLAYER("a", 1), 1)).FROM(G));
        assertFalse(alone.isEmpty());
        Set<List<Integer>> together = new HashSet<>();
        for (Result r : SELECT(AND(POSITION(PLAYER("a", 1), 1), POSITION(PLAYER("b", 1), 1))).FROM(G).search()) {
            together.add(List.of(r.events.getFirst().interval.start, r.events.getFirst().interval.end));
        }
        Factory.reset();
        Set<List<Integer>> expected = new HashSet<>();
        for (Answer a : alone) expected.add(List.of(a.start, a.end));
        assertEquals(expected, together);

        // an anonymous player is still another player than a named one
        for (Result r : SELECT(AND(POSITION(PLAYER("a", 1), 1), POSITION(PLAYER("x", "left"), 1, 2, 3, 4))).FROM(G).search()) {
            Result.GroupEvent and = (Result.GroupEvent) r.events.getFirst();
            assertNotEquals(1, ((Result.PositionEvent) and.children.get(1)).playerIds.getFirst());
        }
        Factory.reset();
    }
}
