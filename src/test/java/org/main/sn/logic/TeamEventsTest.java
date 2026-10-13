package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Event;
import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;

import java.io.IOException;
import java.util.*;
import java.util.function.IntPredicate;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.main.sn.dsl.Factory.*;
import static org.maxicp.cp.CPFactory.makeSolver;

/**
 * Team POSITION, POSSESSION and MOVE_TO checked against direct scans of the zone and possession
 * sequences.
 * <ul>
 *   <li>POSITION(TEAM, K(k), zones): the maximal runs where at least k members are in the zones
 *       (negated: are not), whichever members they are on each frame.</li>
 *   <li>POSSESSION(TEAM): the maximal runs from a member's touch to a member's touch with no touch
 *       by anyone else in between; the ball-free frames in between belong to it.</li>
 *   <li>TEAM.MOVETO(zs, ze, k): a group of k members makes the move when it is all in zs, then all
 *       in ze, read as a Player MOVE_TO on the group; the answer reports the k members.</li>
 * </ul>
 * A wildcard member named in another event too gives one answer per identity, as an anonymous
 * player does. One named nowhere else is anonymous: only the set of players the team stands for
 * matters, so each team, or each moving group, gives a single answer. Every expected interval is
 * at least one frame long, as for any top-level event, and starts on frame 1 at the earliest:
 * frame 0 is a dummy frame.
 */
public class TeamEventsTest {

    record Answer(List<Integer> ids, int start, int end) {}

    private static GameStateReconstructionInstance game(String name) throws IOException {
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(BALL().MOVETO(1, 2)).FROM(name).matches.getFirst();
        Factory.reset();
        return g;
    }

    private static int[][] zones(GameStateReconstructionInstance g) {
        return new Position(makeSolver(), g).position;
    }

    private static int[] possession(GameStateReconstructionInstance g) {
        return new Possession(makeSolver(), g, true).result;
    }

    private static List<Integer> side(GameStateReconstructionInstance g, int side) {
        List<Integer> ids = new ArrayList<>();
        for (int i = 1; i < g.teams.length; i++) if (g.teams[i] == side) ids.add(i);
        return ids;
    }

    /** Maximal runs [s, e) of frames 1..n where {@code in} holds. */
    private static List<int[]> runs(int n, IntPredicate in) {
        List<int[]> runs = new ArrayList<>();
        int s = -1;
        for (int f = 1; f <= n + 1; f++) {
            boolean b = f <= n && in.test(f);
            if (b && s < 0) s = f;
            if (!b && s >= 0) {
                runs.add(new int[]{s, f});
                s = -1;
            }
        }
        return runs;
    }

    private static Set<Answer> answersOf(List<Integer> ids, List<int[]> runs) {
        Set<Answer> answers = new HashSet<>();
        for (int[] r : runs) answers.add(new Answer(ids, r[0], r[1]));
        return answers;
    }

    /** The answers of a single-event team query, with the players it reports. */
    private static Set<Answer> answers(SelectExpr query) {
        List<Result> results = query.search();
        Set<Answer> answers = new HashSet<>();
        for (Result r : results) {
            Result.ResultEvent e = r.events.getFirst();
            List<Integer> ids = switch (e) {
                case Result.PositionEvent p -> p.playerIds;
                case Result.PossessionEvent p -> p.playerIds;
                case Result.TeamMoveEvent m -> m.playerIds;
                case Result.PlayerMoveEvent m -> List.of(m.playerId);
                default -> throw new AssertionError("unexpected event " + e.type);
            };
            answers.add(new Answer(ids, e.interval.start, e.interval.end));
        }
        assertEquals(results.size(), answers.size(), "duplicate answers");
        return answers;
    }

    private static Set<Answer> run(SelectExpr query) {
        Factory.reset();
        return answers(query);
    }

    /** The answers with their players sorted, checking that no team or group comes twice. */
    private static Set<Answer> sortedIds(Set<Answer> answers) {
        Set<Answer> sorted = new HashSet<>();
        for (Answer a : answers) sorted.add(new Answer(a.ids.stream().sorted().toList(), a.start, a.end));
        assertEquals(answers.size(), sorted.size(), "the same team twice");
        return sorted;
    }

    private static Set<int[]> intervals(Set<Answer> answers) {
        Set<int[]> out = new TreeSet<>(Comparator.<int[]>comparingInt(a -> a[0]).thenComparingInt(a -> a[1]));
        for (Answer a : answers) out.add(new int[]{a.start, a.end});
        return out;
    }

    private static List<List<Integer>> intervalList(Set<Answer> answers) {
        return intervals(answers).stream().map(a -> List.of(a[0], a[1])).toList();
    }

    // ########
    // POSITION
    // ########

    /** Frames where at least k of the ids are in (negated: out of) the zones. */
    private static List<int[]> countRuns(int[][] Z, List<Integer> ids, Set<Integer> zones, int k, boolean negated, int n) {
        return runs(n, f -> {
            int c = 0;
            for (int id : ids) if (zones.contains(Z[id][f]) != negated) c++;
            return c >= k;
        });
    }

    @Test
    public void sidePositionCountsTheMembersOnEveryFrame() throws IOException {
        String G = "SNGS-022";
        GameStateReconstructionInstance g = game(G);
        int[][] Z = zones(g);
        int checked = 0;
        for (String name : List.of("left", "right")) {
            List<Integer> roster = side(g, name.equals("left") ? 0 : 1);
            for (int[] zs : List.of(new int[]{1, 2, 3}, new int[]{4}, new int[]{9, 10, 11, 12})) {
                Set<Integer> set = new HashSet<>();
                for (int z : zs) set.add(z);
                for (int k : new int[]{1, 2, 4}) {
                    for (boolean negated : new boolean[]{false, true}) {
                        Event e = POSITION(TEAM(name), K(k), zs);
                        if (negated) e = NOT(e);
                        List<int[]> expected = countRuns(Z, roster, set, k, negated, g.n);
                        checked += expected.size();
                        assertEquals(answersOf(roster, expected), run(SELECT(e).FROM(G)),
                                name + " K(" + k + ") " + set + (negated ? " negated" : ""));
                    }
                }
            }
        }
        assertTrue(checked > 0);
    }

    @Test
    public void teamStayCoversTheWholeClip() throws IOException {
        // in SNGS-127 players 2 and 5 are in zones 1 to 4 on every frame
        GameStateReconstructionInstance g = game("SNGS-127");
        assertEquals(Set.of(new Answer(List.of(2, 5), 1, g.n + 1)),
                run(SELECT(POSITION(TEAM("t", 2, 5), K(2), 1, 2, 3, 4)).FROM("SNGS-127")));
        assertEquals(Set.of(new Answer(side(g, 0), 1, g.n + 1)),
                run(SELECT(POSITION(TEAM("left"), K(3), 1, 2, 3, 4)).FROM("SNGS-127")));
    }

    @Test
    public void aMemberOutsideTheZonesDoesNotStopTheOthers() throws IOException {
        // in SNGS-127 player 1 has stays in zone 1, player 3 is never there
        GameStateReconstructionInstance g = game("SNGS-127");
        int[][] Z = zones(g);
        List<int[]> stays = countRuns(Z, List.of(1), Set.of(1), 1, false, g.n);
        assertFalse(stays.isEmpty());
        assertEquals(answersOf(List.of(1, 3), stays), run(SELECT(POSITION(TEAM("t", 1, 3), K(1), 1)).FROM("SNGS-127")));
    }

    @Test
    public void wildcardMemberGivesOneAnswerPerIdentity() throws IOException {
        String G = "SNGS-022";
        GameStateReconstructionInstance g = game(G);
        int[][] Z = zones(g);
        int fixed = side(g, 1).getFirst();
        Set<Answer> expected = new HashSet<>();
        for (int x : side(g, 1)) {
            if (x == fixed) continue;
            expected.addAll(answersOf(List.of(x, fixed), countRuns(Z, List.of(x, fixed), Set.of(1, 2, 3), 1, false, g.n)));
        }
        assertFalse(expected.isEmpty());
        assertEquals(expected, run(SELECT(POSITION(TEAM("t", PLAYER("x", "right"), PLAYER("p", fixed)), K(1), 1, 2, 3)).FROM(G)));
    }

    /** Whether {@code id} is seen outside the circle (x, y, r) on frame f: never when it is not tracked there. */
    private static boolean seenOutside(GameStateReconstructionInstance g, int id, int f, int[] circle) {
        GameStateReconstructionInstance.PlayerInfo p = g.positions.get(f).players.get(id);
        if (p == null) return false;
        int dx = (int) p.pos().x() - circle[0], dy = (int) p.pos().y() - circle[1];
        return dx * dx + dy * dy > circle[2] * circle[2];
    }

    /** A circle around every position, seen during [run[0], run[1]), of the members counted there. */
    private static int[] circleAround(GameStateReconstructionInstance g, int[][] Z, List<Integer> ids, Set<Integer> zones,
                                      boolean negated, int[] run) {
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int f = run[0]; f < run[1]; f++) {
            for (int id : ids) {
                GameStateReconstructionInstance.PlayerInfo p = g.positions.get(f).players.get(id);
                if (p == null || zones.contains(Z[id][f]) == negated) continue;
                minX = Math.min(minX, (int) p.pos().x()); maxX = Math.max(maxX, (int) p.pos().x());
                minY = Math.min(minY, (int) p.pos().y()); maxY = Math.max(maxY, (int) p.pos().y());
            }
        }
        return new int[]{(minX + maxX) / 2, (minY + maxY) / 2, (int) Math.ceil(Math.hypot(maxX - minX, maxY - minY) / 2) + 1};
    }

    @Test
    public void spatialWhereKeepsTheRunsWhereKMembersStayInsideTheShape() throws IOException {
        // As for a player, a spatial WHERE filters the runs found without it: a run is kept when, on
        // each of its frames, at least k members are in (negated: out of) the zones and not seen
        // outside the shape; an untracked member is never seen outside. The circle is drawn around
        // the members counted during one run, so that the WHERE keeps at least that run, and the run
        // is chosen so that this filter differs from refining the zones with the shape, which would
        // give the maximal runs of that frame condition instead.
        String G = "SNGS-022";
        GameStateReconstructionInstance g = game(G);
        int[][] Z = zones(g);
        record Setting(String side, int k, int[] zones, boolean negated) {}
        for (Setting s : List.of(new Setting("right", 3, new int[]{1, 2, 3, 4}, false),
                new Setting("left", 8, new int[]{1, 2}, true))) {
            String label = s.side + " K(" + s.k + ") " + Arrays.toString(s.zones) + (s.negated ? " negated" : "");
            List<Integer> roster = side(g, s.side.equals("left") ? 0 : 1);
            Set<Integer> set = new HashSet<>();
            for (int z : s.zones) set.add(z);
            List<int[]> all = countRuns(Z, roster, set, s.k, s.negated, g.n);

            int[] circle = null;
            Set<Answer> expected = null;
            for (int[] one : all) {
                if (one[1] - one[0] >= 200) continue;
                int[] c = circleAround(g, Z, roster, set, s.negated, one);
                IntPredicate holds = f -> {
                    int count = 0;
                    for (int id : roster) if (set.contains(Z[id][f]) != s.negated && !seenOutside(g, id, f, c)) count++;
                    return count >= s.k;
                };
                List<int[]> kept = all.stream().filter(r -> IntStream.range(r[0], r[1]).allMatch(holds)).toList();
                Set<Answer> filtered = answersOf(roster, kept);
                if (!filtered.equals(answersOf(roster, runs(g.n, holds)))) {
                    assertTrue(filtered.contains(new Answer(roster, one[0], one[1])), label);
                    assertTrue(kept.size() < all.size(), label);
                    circle = c;
                    expected = filtered;
                    break;
                }
            }
            assertNotNull(circle, label);

            Event e = POSITION(TEAM(s.side), K(s.k), s.zones);
            if (s.negated) e = NOT(e);
            assertEquals(expected, run(SELECT(e).FROM(G).WHERE(RADIUS(circle[0], circle[1], circle[2]))), label);
        }
    }

    @Test
    public void teamMembersMayBeNamedElsewhere() throws IOException {
        // a team member and a separately named player can be the same person
        Set<Answer> player = run(SELECT(POSITION(PLAYER("p", 1), 1)).FROM("SNGS-127"));
        assertFalse(player.isEmpty());
        Factory.reset();
        List<Result> both = SELECT(AND(POSITION(TEAM("t", 1), 1), POSITION(PLAYER("p", 1), 1))).FROM("SNGS-127").search();
        Set<List<Integer>> groups = new HashSet<>();
        for (Result r : both) groups.add(List.of(r.events.getFirst().interval.start, r.events.getFirst().interval.end));
        assertEquals(new HashSet<>(intervalList(player)), groups);
    }

    @Test
    public void teamPositionsInsideOr() throws IOException {
        String G = "SNGS-022";
        Set<Answer> left = run(SELECT(POSITION(TEAM("left"), K(3), 1, 2)).FROM(G));
        Set<Answer> right = run(SELECT(POSITION(TEAM("right"), K(3), 3, 4)).FROM(G));
        Factory.reset();
        List<Result> or = SELECT(OR(POSITION(TEAM("left"), K(3), 1, 2), POSITION(TEAM("right"), K(3), 3, 4))).FROM(G).search();
        assertFalse(left.isEmpty());
        assertFalse(right.isEmpty());
        assertEquals(left.size() + right.size(), or.size());
    }

    // ##########
    // POSSESSION
    // ##########

    /** The frames of the team's possession, scanned outwards from each ball-free frame. */
    private static boolean teamHasBall(int[] P, Set<Integer> team, int f, int n) {
        if (P[f] != 0) return team.contains(P[f]);
        int before = f - 1;
        while (before >= 1 && P[before] == 0) before--;
        int after = f + 1;
        while (after <= n && P[after] == 0) after++;
        return before >= 1 && after <= n && team.contains(P[before]) && team.contains(P[after]);
    }

    @Test
    public void teamKeepsTheBallBetweenTouches() throws IOException {
        String G = "SNGS-060";
        GameStateReconstructionInstance g = game(G);
        int[] P = possession(g);
        int bridged = 0;
        for (String name : List.of("left", "right")) {
            List<Integer> roster = side(g, name.equals("left") ? 0 : 1);
            Set<Integer> team = new HashSet<>(roster);
            for (boolean negated : new boolean[]{false, true}) {
                List<int[]> expected = runs(g.n, f -> teamHasBall(P, team, f, g.n) != negated);
                Event e = POSSESSION(TEAM(name));
                if (negated) e = NOT(e);
                assertEquals(answersOf(roster, expected), run(SELECT(e).FROM(G)), name + (negated ? " negated" : ""));
            }
            // a pass between two members, the case being tested
            for (int f = 1; f <= g.n; f++) if (P[f] == 0 && teamHasBall(P, team, f, g.n)) bridged++;
        }
        assertTrue(bridged > 0);
    }

    @Test
    public void customTeamPossession() throws IOException {
        String G = "SNGS-060";
        GameStateReconstructionInstance g = game(G);
        int[] P = possession(g);
        List<Integer> holders = new ArrayList<>(new TreeSet<>(side(g, 0).stream().filter(id -> Arrays.stream(P).anyMatch(v -> v == id)).toList()));
        int a = holders.get(0), b = holders.get(1);
        List<int[]> expected = runs(g.n, f -> teamHasBall(P, Set.of(a, b), f, g.n));
        assertEquals(answersOf(List.of(a, b), expected), run(SELECT(POSSESSION(TEAM("t", a, b))).FROM(G)));
    }

    @Test
    public void wildcardTeamPossessionGivesOneAnswerPerIdentity() throws IOException {
        String G = "SNGS-060";
        GameStateReconstructionInstance g = game(G);
        int[] P = possession(g);
        List<Integer> left = side(g, 0);
        for (boolean negated : new boolean[]{false, true}) {
            Set<Answer> expected = new HashSet<>();
            for (int a : left) {
                expected.addAll(answersOf(List.of(a), runs(g.n, f -> teamHasBall(P, Set.of(a), f, g.n) != negated)));
            }
            Event e = POSSESSION(TEAM("t", PLAYER("a", "left")));
            if (negated) e = NOT(e);
            assertEquals(expected, run(SELECT(e).FROM(G)), negated ? "negated" : "positive");
        }
        // two anonymous members: one answer per pair of players, whichever member stands for whom
        Set<Answer> expected = new HashSet<>();
        for (int a : left) {
            for (int b : left) {
                if (a >= b) continue;
                expected.addAll(answersOf(List.of(a, b), runs(g.n, f -> teamHasBall(P, Set.of(a, b), f, g.n))));
            }
        }
        assertEquals(expected, sortedIds(run(SELECT(POSSESSION(TEAM("t", PLAYER("a", "left"), PLAYER("b", "left")))).FROM(G))));
    }

    /** Where a player, or the ball, is on frame f; (0, 0) when it is not tracked there. */
    private static int[] at(GameStateReconstructionInstance g, int id, int f) {
        GameStateReconstructionInstance.PlayerInfo p = f >= 1 && f <= g.n ? g.positions.get(f).players.get(id) : null;
        return p == null ? new int[]{0, 0} : new int[]{(int) p.pos().x(), (int) p.pos().y()};
    }

    @Test
    public void spatialWhereOnTeamPossessionTestsTheBall() throws IOException {
        String G = "SNGS-061";
        GameStateReconstructionInstance g = game(G);
        List<Integer> left = side(g, 0);
        Set<Answer> all = run(SELECT(POSSESSION(TEAM("left"))).FROM(G));
        Answer first = all.stream().min(Comparator.comparingInt(Answer::start)).orElseThrow();

        // a circle around the ball during that possession, give or take a frame
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int f = first.start - 1; f <= first.end; f++) {
            int[] b = at(g, g.ball_idx, f);
            minX = Math.min(minX, b[0]); maxX = Math.max(maxX, b[0]);
            minY = Math.min(minY, b[1]); maxY = Math.max(maxY, b[1]);
        }
        int cx = (minX + maxX) / 2, cy = (minY + maxY) / 2;
        int r = (int) Math.ceil(Math.hypot(maxX - minX, maxY - minY) / 2) + 2;
        // the members do not all stay in it, so it is the ball that is tested
        boolean memberOutside = false;
        for (int f = first.start; f < first.end; f++) {
            for (int id : left) {
                int[] p = at(g, id, f);
                if (g.positions.get(f).players.containsKey(id) && Math.hypot(p[0] - cx, p[1] - cy) > r) memberOutside = true;
            }
        }
        assertTrue(memberOutside);

        Set<Answer> near = run(SELECT(POSSESSION(TEAM("left"))).FROM(G).WHERE(RADIUS(cx, cy, r)));
        assertTrue(near.contains(first));
        assertTrue(all.containsAll(near));
        assertTrue(run(SELECT(POSSESSION(TEAM("left"))).FROM(G).WHERE(RADIUS(55, 30, 3))).isEmpty());

        // a custom team given by numbers used to throw here
        Factory.reset();
        assertDoesNotThrow(() -> SELECT(POSSESSION(TEAM("t", left.get(0), left.get(1)))).FROM(G).WHERE(RADIUS(0, 0, 100)).search());
    }

    // #######
    // MOVE_TO
    // #######

    /**
     * Moves zs to ze of the group: 1 where all its members are in zs, 2 where all are in ze. As in
     * MoveToTest, a move is a maximal run of 1 followed by a maximal run of 2 with only 0 in between;
     * the extended interval spans both runs, the MINRANGE one runs from the last frame of the first
     * to the first frame of the second.
     */
    private static Set<Answer> groupMoves(int[][] Z, List<Integer> group, int zs, int ze, boolean minrange, int n) {
        int[] w = new int[n + 1];
        for (int f = 1; f <= n; f++) {
            boolean allA = true, allB = true;
            for (int id : group) {
                allA &= Z[id][f] == zs;
                allB &= Z[id][f] == ze;
            }
            w[f] = allA ? 1 : allB ? 2 : 0;
        }
        List<int[]> runs = new ArrayList<>();
        for (int s = 1, e; s <= n; s = e) {
            e = s + 1;
            while (e <= n && w[e] == w[s]) e++;
            runs.add(new int[]{w[s], s, e});
        }
        Set<Answer> moves = new HashSet<>();
        for (int i = 0; i < runs.size(); i++) {
            if (runs.get(i)[0] != 1) continue;
            for (int j = i + 1; j < runs.size() && runs.get(j)[0] != 1; j++) {
                if (runs.get(j)[0] == 2) {
                    int[] a = runs.get(i), b = runs.get(j);
                    moves.add(minrange ? new Answer(group, a[2] - 1, b[1] + 1) : new Answer(group, a[1], b[2]));
                    break;
                }
            }
        }
        return moves;
    }

    private static void groups(List<Integer> ids, int k, int from, Deque<Integer> current, List<List<Integer>> out) {
        if (current.size() == k) {
            out.add(new ArrayList<>(current));
            return;
        }
        for (int i = from; i < ids.size(); i++) {
            current.addLast(ids.get(i));
            groups(ids, k, i + 1, current, out);
            current.removeLast();
        }
    }

    @Test
    public void teamMovesAreGroupMoves() throws IOException {
        // in SNGS-049 several groups of three left players move from zone 1 to zone 2
        String G = "SNGS-049";
        GameStateReconstructionInstance g = game(G);
        int[][] Z = zones(g);
        List<Integer> left = side(g, 0);
        for (int k : new int[]{2, 3}) {
            List<List<Integer>> all = new ArrayList<>();
            groups(left, k, 0, new ArrayDeque<>(), all);
            for (boolean minrange : new boolean[]{false, true}) {
                Set<Answer> expected = new HashSet<>();
                for (List<Integer> group : all) expected.addAll(groupMoves(Z, group, 1, 2, minrange, g.n));
                assertFalse(expected.isEmpty());
                Event e = TEAM("left").MOVETO(1, 2, k);
                if (minrange) e = e.MINRANGE();
                assertEquals(expected, run(SELECT(e).FROM(G)), "k = " + k + (minrange ? " MINRANGE" : ""));
            }
        }
    }

    @Test
    public void aTeamMoveOfOnePlayerIsAPlayerMove() throws IOException {
        String G = "SNGS-049";
        for (boolean minrange : new boolean[]{false, true}) {
            Event team = TEAM("left").MOVETO(1, 2, 1);
            if (minrange) team = team.MINRANGE();
            Set<Answer> teamMoves = run(SELECT(team).FROM(G));
            Event player = PLAYER("mover", "left").MOVETO(1, 2);
            if (minrange) player = player.MINRANGE();
            assertFalse(teamMoves.isEmpty());
            assertEquals(run(SELECT(player).FROM(G)), teamMoves, minrange ? "MINRANGE" : "extended");
        }
    }

    @Test
    public void anonymousMembersGiveOneAnswerPerGroup() throws IOException {
        // three anonymous members, two of which move: each pair of players moving together, once
        String G = "SNGS-049";
        GameStateReconstructionInstance g = game(G);
        int[][] Z = zones(g);
        List<Integer> everyone = new ArrayList<>(side(g, 0));
        everyone.addAll(side(g, 1));
        Collections.sort(everyone);
        List<List<Integer>> pairs = new ArrayList<>();
        groups(everyone, 2, 0, new ArrayDeque<>(), pairs);
        Set<Answer> expected = new HashSet<>();
        for (List<Integer> pair : pairs) expected.addAll(groupMoves(Z, pair, 1, 2, true, g.n));
        assertFalse(expected.isEmpty());
        assertEquals(expected, sortedIds(run(SELECT(TEAM("t", PLAYER("a"), PLAYER("b"), PLAYER("c")).MOVETO(1, 2, 2).MINRANGE()).FROM(G))));
    }

    @Test
    public void aMemberNamedElsewhereKeepsItsRole() throws IOException {
        // a is named by both events, so it is one of the two left players that move, and the one in zone 2
        String G = "SNGS-049";
        GameStateReconstructionInstance g = game(G);
        int[][] Z = zones(g);
        List<List<Integer>> pairs = new ArrayList<>();
        groups(side(g, 0), 2, 0, new ArrayDeque<>(), pairs);
        int expected = 0;
        for (List<Integer> pair : pairs) {
            for (Answer move : groupMoves(Z, pair, 1, 2, true, g.n)) {
                for (int a : pair) {
                    // the stays of a in zone 2 that overlap the move on at least one frame
                    for (int s = 1, e; s <= g.n; s = e) {
                        e = s + 1;
                        boolean in = Z[a][s] == 2;
                        while (e <= g.n && (Z[a][e] == 2) == in) e++;
                        if (in && Math.min(move.end, e) - Math.max(move.start, s) >= 1) expected++;
                    }
                }
            }
        }
        assertTrue(expected > 0);
        Factory.reset();
        List<Result> results = SELECT(AND(TEAM("t", PLAYER("a", "left"), PLAYER("b", "left")).MOVETO(1, 2, 2).MINRANGE(),
                POSITION(PLAYER("a", "left"), 2))).FROM(G).search();
        assertEquals(expected, results.size());
        for (Result r : results) {
            Result.GroupEvent and = (Result.GroupEvent) r.events.getFirst();
            Result.TeamMoveEvent move = (Result.TeamMoveEvent) and.children.get(0);
            Result.PositionEvent position = (Result.PositionEvent) and.children.get(1);
            assertTrue(move.playerIds.contains(position.playerIds.getFirst()));
        }
    }

    @Test
    public void aMemberThatNeverMovesDoesNotStopTheOthers() throws IOException {
        // in SNGS-025 player 3 moves from zone 1 to zone 5, player 4 is never in zone 1
        GameStateReconstructionInstance g = game("SNGS-025");
        int[][] Z = zones(g);
        Set<Answer> expected = groupMoves(Z, List.of(3), 1, 5, true, g.n);
        assertFalse(expected.isEmpty());
        assertEquals(expected, run(SELECT(TEAM("t", 3, 4).MOVETO(1, 5, 1).MINRANGE()).FROM("SNGS-025")));
    }
}
