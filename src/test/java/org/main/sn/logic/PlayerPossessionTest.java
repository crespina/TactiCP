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
 * Player POSSESSION checked against a direct scan of the possession sequence, where a player keeps
 * the ball from one touch to the next as long as nobody else touches it. In SNGS-021 the right
 * players 5 and 6 hand the ball to each other seven times, and thirteen players never hold it.
 * Every expected interval is at least one frame long, as for any top-level event.
 */
public class PlayerPossessionTest {

    private static final String GAME = "SNGS-021";

    record Stay(int player, int start, int end) {}

    private static GameStateReconstructionInstance game() throws IOException {
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(POSSESSION(PLAYER("loader"))).FROM(GAME).matches.getFirst();
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

    private static Set<Integer> side(GameStateReconstructionInstance g, int team) {
        Set<Integer> players = new HashSet<>();
        for (int i = 0; i < g.teams.length; i++) if (g.teams[i] == team) players.add(i);
        return players;
    }

    /** Maximal runs [s, e) in which one single player of {@code candidates} holds the ball. */
    private static Set<Stay> holding(int[] possession, Set<Integer> candidates) {
        Set<Stay> stays = new HashSet<>();
        for (int s = 1, e = 2; s < possession.length; s = e, e = s + 1) {
            while (e < possession.length && possession[e] == possession[s]) e++;
            if (candidates.contains(possession[s])) stays.add(new Stay(possession[s], s, e));
        }
        return stays;
    }

    /** Maximal runs [s, e) in which player c does not hold the ball, from frame 1: not the dummy frame 0. */
    private static Set<Stay> notHolding(int[] possession, int c) {
        Set<Stay> stays = new HashSet<>();
        for (int s = 1, e = 2; s < possession.length; s = e, e = s + 1) {
            boolean free = possession[s] != c;
            while (e < possession.length && (possession[e] != c) == free) e++;
            if (free) stays.add(new Stay(c, s, e));
        }
        return stays;
    }

    /** The answers of a single-event POSSESSION query. */
    private static Set<Stay> answers(SelectExpr query) {
        List<Result> results = query.search();
        Set<Stay> stays = new HashSet<>();
        for (Result r : results) {
            Result.PossessionEvent e = (Result.PossessionEvent) r.events.getFirst();
            stays.add(new Stay(e.playerIds.getFirst(), e.interval.start, e.interval.end));
        }
        assertEquals(results.size(), stays.size(), "duplicate answers");
        return stays;
    }

    @Test
    public void stayEndsAtDirectHandover() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] possession = possession(g);
        Set<Integer> right = side(g, 1);

        Set<Stay> expected = holding(possession, right);
        // a right player's stay followed straight by another right player's: the case being tested
        assertTrue(expected.stream().anyMatch(a -> expected.stream()
                .anyMatch(b -> a.end() == b.start() && a.player() != b.player())));
        assertEquals(expected, answers(SELECT(POSSESSION(PLAYER("holder", "right"))).FROM(GAME)));

        Factory.reset();
        Set<Integer> everyone = new HashSet<>(right);
        everyone.addAll(side(g, 0));
        assertEquals(holding(possession, everyone), answers(SELECT(POSSESSION(PLAYER("anyone"))).FROM(GAME)));
    }

    @Test
    public void stayGoesOnBetweenTouches() throws IOException {
        // in SNGS-060 several players lose the ball for a few frames and touch it again before anyone else
        String G = "SNGS-060";
        Factory.reset();
        GameStateReconstructionInstance g = SELECT(POSSESSION(PLAYER("loader"))).FROM(G).matches.getFirst();
        int[] raw = new Possession(makeSolver(), g, true).result;
        int[] bridged = possession(g);
        Set<Integer> everyone = new HashSet<>(side(g, 0));
        everyone.addAll(side(g, 1));
        Set<Stay> expected = holding(bridged, everyone);
        // a stay with a ball-free frame inside: the case being tested
        assertTrue(expected.stream().anyMatch(st -> Arrays.stream(raw, st.start(), st.end()).anyMatch(v -> v == 0)));
        Factory.reset();
        assertEquals(expected, answers(SELECT(POSSESSION(PLAYER("anyone"))).FROM(G)));
    }

    @Test
    public void stayOfAGivenPlayer() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] possession = possession(g);
        for (int id : side(g, 0)) {
            Factory.reset();
            assertEquals(holding(possession, Set.of(id)), answers(SELECT(POSSESSION(PLAYER("given", id))).FROM(GAME)));
        }
    }

    @Test
    public void negationIsPerPlayer() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] possession = possession(g);
        Set<Stay> expected = new HashSet<>();
        for (int c : side(g, 0)) expected.addAll(notHolding(possession, c));

        assertEquals(expected, answers(SELECT(NOT(POSSESSION(PLAYER("free", "left")))).FROM(GAME)));
    }

    @Test
    public void negationCoversTheWholeClip() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] possession = possession(g);
        int idle = side(g, 0).stream()
                .filter(c -> Arrays.stream(possession).noneMatch(v -> v == c))
                .findFirst().orElseThrow();

        // from the first real frame to the last one
        assertEquals(Set.of(new Stay(idle, 1, possession.length)),
                answers(SELECT(NOT(POSSESSION(PLAYER("idle", idle)))).FROM(GAME)));
    }

    @Test
    public void negationKeepsTheSharedIdentity() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] possession = possession(g);

        List<Result> results = SELECT(AND(
                POSITION(PLAYER("p", "left"), 13),
                NOT(POSSESSION(PLAYER("p", "left"))))).FROM(GAME).search();
        assertFalse(results.isEmpty());
        for (Result r : results) {
            Result.GroupEvent and = (Result.GroupEvent) r.events.getFirst();
            int inZone = ((Result.PositionEvent) and.children.get(0)).playerIds.getFirst();
            Result.PossessionEvent free = (Result.PossessionEvent) and.children.get(1);
            int withoutBall = free.playerIds.getFirst();
            assertEquals(inZone, withoutBall);
            assertTrue(isMaximalWithout(possession, withoutBall, free.interval.start, free.interval.end));
        }
    }

    @Test
    public void negationInsideOr() throws IOException {
        GameStateReconstructionInstance g = game();
        int[] possession = possession(g);
        Set<Stay> expected = new HashSet<>();
        for (int c : side(g, 0)) expected.addAll(notHolding(possession, c));

        Set<Stay> throughNot = new HashSet<>();
        for (Result r : SELECT(OR(NOT(POSSESSION(PLAYER("free", "left"))), POSITION(BALL(), 13))).FROM(GAME).search()) {
            Result.ResultEvent taken = ((Result.GroupEvent) r.events.getFirst()).children.getFirst();
            if (taken instanceof Result.PossessionEvent e) {
                throughNot.add(new Stay(e.playerIds.getFirst(), e.interval.start, e.interval.end));
            }
        }
        assertEquals(expected, throughNot);
    }

    /** [start, end) is a maximal run without c. */
    private static boolean isMaximalWithout(int[] possession, int c, int start, int end) {
        for (int f = start; f < end; f++) if (possession[f] == c) return false;
        return start >= 1 && (start == 1 || possession[start - 1] == c) && (end == possession.length || possession[end] == c);
    }
}
