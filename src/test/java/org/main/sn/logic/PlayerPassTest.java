package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;

import java.io.IOException;
import java.util.*;
import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.main.sn.dsl.Factory.*;
import static org.maxicp.cp.CPFactory.makeSolver;

/**
 * PASSTO checked against a direct scan of the possession sequence, where a pass is three
 * consecutive maximal runs: a player a, frames where nobody holds the ball, a player b != a of
 * the same team. A player keeps the ball from one touch to the next as long as nobody else touches
 * it. In SNGS-021, player 5 gets the ball straight from player 6 and passes it back.
 */
public class PlayerPassTest {

    private static final String GAME = "SNGS-021";

    record Pass(int passer, int receiver, int start, int end) {}

    private static GameStateReconstructionInstance game() throws IOException {
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(POSSESSION(PLAYER("loader"))).FROM(GAME).games().getFirst();
        Factory.reset();
        return g;
    }

    /**
     * The possession sequence the events read: the ball-free frames between two touches by the same
     * player belong to that player.
     */
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

    /**
     * Every pass of the sequence: the extended one spans the stays of a and b, the MINRANGE one
     * runs from the last frame of a to the first frame of b.
     */
    private static Set<Pass> passes(int[] possession, int[] teams, IntPredicate passer, IntPredicate receiver, boolean minrange) {
        List<int[]> runs = new ArrayList<>(); // {player, start, end}
        for (int s = 0, e = 1; s < possession.length; s = e, e = s + 1) {
            while (e < possession.length && possession[e] == possession[s]) e++;
            runs.add(new int[]{possession[s], s, e});
        }
        Set<Pass> passes = new HashSet<>();
        for (int i = 0; i + 2 < runs.size(); i++) {
            int[] from = runs.get(i), to = runs.get(i + 2);
            int a = from[0], b = to[0];
            if (a == 0 || runs.get(i + 1)[0] != 0 || b == 0 || b == a || teams[a] != teams[b]) continue;
            if (!passer.test(a) || !receiver.test(b)) continue;
            passes.add(minrange ? new Pass(a, b, from[2] - 1, to[1] + 1) : new Pass(a, b, from[1], to[2]));
        }
        return passes;
    }

    private static Set<Pass> answers(SelectExpr query) {
        List<Result> results = query.search();
        Set<Pass> passes = new HashSet<>();
        for (Result r : results) {
            Result.PassEvent e = (Result.PassEvent) r.events.getFirst();
            passes.add(new Pass(e.passerId, e.receiverId, e.interval.start, e.interval.end));
        }
        assertEquals(results.size(), passes.size(), "duplicate answers");
        return passes;
    }

    @Test
    public void passAfterDirectHandover() throws IOException {
        int[] possession = possession(game());
        // 6 hands the ball straight to 5, who passes it back to 6
        assertEquals(6, possession[20]);
        assertEquals(5, possession[21]);

        assertTrue(answers(SELECT(PLAYER("a").PASSTO(PLAYER("b"))).FROM(GAME)).contains(new Pass(5, 6, 21, 44)));
        Factory.reset();
        assertTrue(answers(SELECT(PLAYER("a", "right").PASSTO(PLAYER("b"))).FROM(GAME)).contains(new Pass(5, 6, 21, 44)));
    }

    @Test
    public void passesMatchTheScan() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] possession = possession(g);
        int[] teams = g.teams;
        IntPredicate anyone = p -> true, right = p -> teams[p] == 1;
        for (boolean minrange : new boolean[]{false, true}) {
            Factory.reset();
            assertEquals(passes(possession, teams, anyone, anyone, minrange), answers(SELECT(minrange
                    ? PLAYER("a").PASSTO(PLAYER("b")).MINRANGE()
                    : PLAYER("a").PASSTO(PLAYER("b"))).FROM(GAME)));
            Factory.reset();
            assertEquals(passes(possession, teams, right, anyone, minrange), answers(SELECT(minrange
                    ? PLAYER("a", "right").PASSTO(PLAYER("b")).MINRANGE()
                    : PLAYER("a", "right").PASSTO(PLAYER("b"))).FROM(GAME)));
            Factory.reset();
            assertEquals(passes(possession, teams, anyone, right, minrange), answers(SELECT(minrange
                    ? PLAYER("a").PASSTO(PLAYER("b", "right")).MINRANGE()
                    : PLAYER("a").PASSTO(PLAYER("b", "right"))).FROM(GAME)));
        }
    }

    @Test
    public void passerKeepsTheBallBetweenTouches() throws IOException {
        // in SNGS-060 some passers lose the ball for a few frames and touch it again before passing
        String G = "SNGS-060";
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(POSSESSION(PLAYER("loader"))).FROM(G).games().getFirst();
        int[] raw = new Possession(makeSolver(), g, true).result;
        int[] bridged = possession(g);
        Set<Pass> expected = passes(bridged, g.teams, p -> true, p -> true, false);
        // a pass whose passer has the ball-free frames inside its stay: the case being tested
        assertTrue(expected.stream().anyMatch(p -> {
            int f = p.start();
            while (bridged[f] == p.passer()) f++;
            return Arrays.stream(raw, p.start(), f).anyMatch(v -> v == 0);
        }));
        Factory.reset();
        assertEquals(expected, answers(SELECT(PLAYER("a").PASSTO(PLAYER("b"))).FROM(G)));
        Factory.reset();
        assertEquals(passes(bridged, g.teams, p -> true, p -> true, true),
                answers(SELECT(PLAYER("a").PASSTO(PLAYER("b")).MINRANGE()).FROM(G)));
    }

    @Test
    public void passesOfAGivenPlayerMatchTheScan() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] possession = possession(g);
        for (int id : new int[]{5, 6}) {
            Factory.reset();
            assertEquals(passes(possession, g.teams, p -> p == id, p -> true, false),
                    answers(SELECT(PLAYER("given", id).PASSTO(PLAYER("b"))).FROM(GAME)));
        }
    }
}
