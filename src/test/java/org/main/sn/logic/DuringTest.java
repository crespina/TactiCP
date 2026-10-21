package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Event;
import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;

import java.io.IOException;
import java.util.*;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.main.sn.dsl.Factory.*;
import static org.maxicp.cp.CPFactory.makeSolver;

/**
 * DURING(a, b), checked against direct scans. Like a shape, it holds frame by frame: a POSITION or
 * a POSSESSION keeps its frames from a to b, so its answers are the maximal runs of the state in
 * that window, and a bridged possession may then start or end on a ball-free frame. A move or a
 * pass must lie inside the window, as with STARTMIN(a) and ENDMAX(b). Written on an event, on a
 * group or on the whole query, it applies to each atomic event, and nested windows intersect.
 */
public class DuringTest {

    record Answer(int who, int start, int end) {}

    private static GameStateReconstructionInstance game(String name) throws IOException {
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(BALL().MOVETO(1, 2)).FROM(name).matches.getFirst();
        Factory.reset();
        return g;
    }

    private static int[][] zones(GameStateReconstructionInstance g) {
        return new Position(makeSolver(), g).position;
    }

    private static int[] rawPossession(GameStateReconstructionInstance g) {
        return new Possession(makeSolver(), g, true).result;
    }

    /** The possession sequence the events read: ball-free frames between two touches by one player are that player's. */
    private static int[] possession(GameStateReconstructionInstance g) {
        int[] raw = rawPossession(g);
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

    /** Maximal runs of the frames 1..n where {@code in} holds. */
    private static Set<Answer> runs(int who, int n, IntPredicate in) {
        Set<Answer> runs = new HashSet<>();
        for (int s = 1, e; s <= n; s = e) {
            e = s + 1;
            if (!in.test(s)) continue;
            while (e <= n && in.test(e)) e++;
            runs.add(new Answer(who, s, e));
        }
        return runs;
    }

    /** The answers of a single-event query; the ball is reported as -1, a team by its first member. */
    private static Set<Answer> run(SelectExpr query) {
        List<Result> results = query.search();
        Set<Answer> answers = new HashSet<>();
        for (Result r : results) {
            Result.ResultEvent e = r.events.getFirst();
            int who = switch (e) {
                case Result.PositionEvent p -> p.playerIds.isEmpty() ? -1 : p.playerIds.getFirst();
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

    /** Every answer of a query, as its formatted text without its number. */
    private static Set<String> texts(SelectExpr query) {
        Set<String> texts = new HashSet<>();
        for (Result r : query.search()) {
            List<String> lines = r.toFormattedStrings();
            texts.add(String.join("\n", lines.subList(1, lines.size())));
        }
        Factory.reset();
        return texts;
    }

    private static Set<Answer> inWindow(Set<Answer> answers, int a, int b) {
        Set<Answer> kept = new HashSet<>();
        for (Answer x : answers) if (x.start >= a && x.end - 1 <= b) kept.add(x);
        return kept;
    }

    @Test
    public void positionsAreCutToTheWindow() throws IOException {
        String G = "SNGS-022";
        GameStateReconstructionInstance g = game(G);
        int[][] Z = zones(g);
        Set<Integer> zs = Set.of(1, 2, 3, 4);
        int b = g.ball_idx;
        // a window that starts and ends inside stays of the ball
        Set<Answer> all = run(SELECT(POSITION(BALL(), 1, 2, 3, 4)).FROM(G));
        List<Answer> ball = all.stream().filter(x -> x.end - x.start >= 4).sorted(Comparator.comparingInt(Answer::start)).toList();
        int from = ball.getFirst().start + 1, to = ball.getLast().end - 3;
        assertTrue(from <= to);
        IntPredicate window = f -> f >= from && f <= to;

        // the stays crossing the window are cut, not dropped
        Set<Answer> cut = runs(-1, g.n, f -> zs.contains(Z[b][f]) && window.test(f));
        assertNotEquals(inWindow(all, from, to), cut);

        for (boolean negated : new boolean[]{false, true}) {
            String label = negated ? "negated" : "positive";

            Set<Answer> expected = runs(-1, g.n, f -> zs.contains(Z[b][f]) != negated && window.test(f));
            Event e = POSITION(BALL(), 1, 2, 3, 4);
            if (negated) e = NOT(e);
            assertEquals(expected, run(SELECT(e.WHERE(DURING(from, to))).FROM(G)), "ball " + label);

            Set<Answer> players = new HashSet<>();
            for (int c : left(g)) players.addAll(runs(c, g.n, f -> zs.contains(Z[c][f]) != negated && window.test(f)));
            e = POSITION(PLAYER("p", "left"), 1, 2, 3, 4);
            if (negated) e = NOT(e);
            assertEquals(players, run(SELECT(e.WHERE(DURING(from, to))).FROM(G)), "player " + label);

            List<Integer> roster = left(g);
            int k = 3;
            Set<Answer> team = runs(roster.getFirst(), g.n, f -> {
                int count = 0;
                for (int id : roster) if (zs.contains(Z[id][f]) != negated) count++;
                return count >= k && window.test(f);
            });
            e = POSITION(TEAM("left"), K(k), 1, 2, 3, 4);
            if (negated) e = NOT(e);
            assertEquals(team, run(SELECT(e.WHERE(DURING(from, to))).FROM(G)), "team " + label);
        }
    }

    @Test
    public void possessionsAreCutToTheWindowEvenOnABallFreeFrame() throws IOException {
        String G = "SNGS-060";
        GameStateReconstructionInstance g = game(G);
        int[] raw = rawPossession(g);
        int[] P = possession(g);
        List<Integer> roster = left(g);
        Set<Integer> team = new HashSet<>(roster);

        // the window starts on a ball-free frame inside a possession by the left team
        IntPredicate teamHasBall = f -> {
            if (raw[f] != 0) return team.contains(raw[f]);
            int before = f - 1;
            while (before >= 1 && raw[before] == 0) before--;
            int after = f + 1;
            while (after <= g.n && raw[after] == 0) after++;
            return before >= 1 && after <= g.n && team.contains(raw[before]) && team.contains(raw[after]);
        };
        int from = -1;
        for (int f = 2; f <= g.n && from < 0; f++) if (raw[f] == 0 && teamHasBall.test(f) && teamHasBall.test(f - 1)) from = f;
        assertTrue(from > 0);
        final int a = from, to = g.n - 50;
        IntPredicate window = f -> f >= a && f <= to;

        Set<Answer> teamExpected = runs(roster.getFirst(), g.n, f -> teamHasBall.test(f) && window.test(f));
        assertTrue(teamExpected.stream().anyMatch(x -> x.start == a), "a team possession cut on a ball-free frame");
        assertEquals(teamExpected, run(SELECT(POSSESSION(TEAM("left")).WHERE(DURING(a, to))).FROM(G)), "team");

        Set<Answer> held = new HashSet<>();
        for (int s = 1, e; s <= g.n; s = e) {
            e = s + 1;
            int c = window.test(s) ? P[s] : 0;
            if (c == 0 || (g.teams[c] != 0 && g.teams[c] != 1)) continue;
            while (e <= g.n && window.test(e) && P[e] == c) e++;
            held.add(new Answer(c, s, e));
        }
        assertEquals(held, run(SELECT(POSSESSION(PLAYER("holder")).WHERE(DURING(a, to))).FROM(G)), "player");

        Set<Answer> notHeld = new HashSet<>();
        for (int c : roster) notHeld.addAll(runs(c, g.n, f -> P[f] != c && window.test(f)));
        assertEquals(notHeld, run(SELECT(NOT(POSSESSION(PLAYER("p", "left"))).WHERE(DURING(a, to))).FROM(G)), "player negated");
    }

    @Test
    public void movesAndPassesMustLieInTheWindow() throws IOException {
        String G = "SNGS-060";
        GameStateReconstructionInstance g = game(G);
        Set<Answer> passes = run(SELECT(PLAYER("a").PASSTO(PLAYER("b"))).FROM(G));
        Set<Answer> moves = Set.of();
        int zs = 0, ze = 0;
        search:
        for (int s = 1; s <= 4; s++) {
            for (int e = 1; e <= 4; e++) {
                if (s == e) continue;
                moves = run(SELECT(BALL().MOVETO(s, e)).FROM(G));
                if (moves.size() >= 2) {
                    zs = s; ze = e;
                    break search;
                }
            }
        }
        assertTrue(moves.size() >= 2);
        assertTrue(passes.size() >= 2);
        for (boolean pass : new boolean[]{true, false}) {
            Set<Answer> all = pass ? passes : moves;
            // the window starts inside the earliest answer, which it drops instead of cutting
            int from = all.stream().mapToInt(Answer::start).min().orElseThrow() + 1, to = g.n;
            Set<Answer> expected = inWindow(all, from, to);
            assertTrue(!expected.isEmpty() && expected.size() < all.size());
            Event e = pass ? PLAYER("a").PASSTO(PLAYER("b")) : BALL().MOVETO(zs, ze);
            assertEquals(expected, run(SELECT(e.WHERE(DURING(from, to))).FROM(G)), pass ? "pass" : "move");
            e = pass ? PLAYER("a").PASSTO(PLAYER("b")) : BALL().MOVETO(zs, ze);
            assertEquals(expected, run(SELECT(e.WHERE(STARTMIN(from), ENDMAX(to))).FROM(G)), pass ? "pass" : "move");
        }
    }

    @Test
    public void aWindowOnTheQueryAppliesToEachStep() throws IOException {
        // in SNGS-022 the ball is in zones 1 and 2 on [477, 554), then in zones 3 and 4 on [554, 679)
        String G = "SNGS-022";
        int from = 500, to = 600;
        Set<String> onTheQuery = texts(SELECT(POSITION(BALL(), 1, 2), POSITION(BALL(), 3, 4)).FROM(G).WHERE(DURING(from, to)));
        Set<String> onEachStep = texts(SELECT(POSITION(BALL(), 1, 2).WHERE(DURING(from, to)),
                POSITION(BALL(), 3, 4).WHERE(DURING(from, to))).FROM(G));
        assertFalse(onTheQuery.isEmpty());
        assertEquals(onEachStep, onTheQuery);
        // not a bound on the span of the sequence: its first and last stays are cut, not dropped
        Set<String> span = texts(SELECT(POSITION(BALL(), 1, 2), POSITION(BALL(), 3, 4)).FROM(G).WHERE(STARTMIN(from), ENDMAX(to)));
        assertNotEquals(span, onTheQuery);
    }

    @Test
    public void nestedWindowsIntersect() throws IOException {
        // in SNGS-022 the ball is in zones 1 to 4 on [403, 412) and [477, 679)
        String G = "SNGS-022";
        Set<String> nested = texts(SELECT(AND(POSITION(BALL(), 1, 2, 3, 4).WHERE(DURING(400, 640)),
                POSITION(PLAYER("p", "left"), 1, 2, 3, 4)).WHERE(DURING(450, 700))).FROM(G).WHERE(DURING(0, 620)));
        Set<String> flat = texts(SELECT(AND(POSITION(BALL(), 1, 2, 3, 4).WHERE(DURING(450, 620)),
                POSITION(PLAYER("p", "left"), 1, 2, 3, 4).WHERE(DURING(450, 620)))).FROM(G));
        assertFalse(flat.isEmpty());
        assertEquals(flat, nested);
        // windows that do not overlap leave nothing, though each one alone keeps a stay
        assertFalse(texts(SELECT(POSITION(BALL(), 1, 2, 3, 4).WHERE(DURING(450, 500))).FROM(G)).isEmpty());
        assertFalse(texts(SELECT(POSITION(BALL(), 1, 2, 3, 4).WHERE(DURING(550, 600))).FROM(G)).isEmpty());
        assertTrue(texts(SELECT(POSITION(BALL(), 1, 2, 3, 4).WHERE(DURING(450, 500))).FROM(G).WHERE(DURING(550, 600))).isEmpty());
    }

    @Test
    public void anEmptyWindowIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DURING(10, 5));
        assertThrows(IllegalArgumentException.class, () -> DURING(-1, 5));
    }
}
