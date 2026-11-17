package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Event;
import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;

import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.main.sn.dsl.Factory.*;

/**
 * ATLEAST, ATMOST and SEARCH, game by game. ATLEAST(n) and ATMOST(n) bound how many answers a game
 * has: a game outside the bounds gives none. SEARCH(k) caps how many it returns: the first k the
 * search finds, among all of them.
 */
public class SearchBoundsTest {

    private static final List<String> GAMES = List.of("SNGS-060", "SNGS-061", "SNGS-022");

    /** The answers of each game, keyed by game, each answer as its first interval. */
    private static Map<String, Set<List<Integer>>> byGame(SelectExpr query) {
        Map<String, Set<List<Integer>>> out = new HashMap<>();
        List<Result> results = query.search();
        for (Result r : results) {
            Result.ResultEvent e = r.events.getFirst();
            out.computeIfAbsent(r.instance, g -> new HashSet<>()).add(List.of(e.interval.start, e.interval.end));
        }
        assertEquals(results.size(), out.values().stream().mapToInt(Set::size).sum(), "duplicate answers");
        Factory.reset();
        return out;
    }

    private static Map<String, Set<List<Integer>>> run(Supplier<Event> event, Function<SelectExpr, SelectExpr> modifiers) throws Exception {
        Factory.reset();
        return byGame(modifiers.apply(SELECT(event.get()).FROM(String.join(",", GAMES))));
    }

    @Test
    public void boundsFilterGamesAndSearchCapsThem() throws Exception {
        // anonymous passes: a few answers per game, and a different number in each game
        Supplier<Event> pass = () -> PLAYER("p1").PASSTO(PLAYER("p2")).MINRANGE();
        Map<String, Set<List<Integer>>> all = run(pass, q -> q);
        Set<Integer> counts = new TreeSet<>();
        for (String g : GAMES) counts.add(all.getOrDefault(g, Set.of()).size());
        assertTrue(counts.size() >= 2, "the games should differ in their number of answers: " + counts);

        // the bounds that matter: 0, and just below, at and just above each game's number of answers
        Set<Integer> bounds = new TreeSet<>(Set.of(0));
        for (int c : counts) bounds.addAll(List.of(Math.max(c - 1, 0), c, c + 1));
        int kept = 0, dropped = 0;
        for (int bound : bounds) {
            Map<String, Set<List<Integer>>> atMost = run(pass, q -> q.WHERE(ATMOST(bound)));
            Map<String, Set<List<Integer>>> atLeast = run(pass, q -> q.WHERE(ATLEAST(bound)));
            for (String g : GAMES) {
                Set<List<Integer>> every = all.getOrDefault(g, Set.of());
                assertEquals(every.size() <= bound ? every : Set.of(), atMost.getOrDefault(g, Set.of()), g + " ATMOST(" + bound + ")");
                assertEquals(every.size() >= bound ? every : Set.of(), atLeast.getOrDefault(g, Set.of()), g + " ATLEAST(" + bound + ")");
                if (every.size() <= bound) kept++; else dropped++;
            }
            for (int k = 1; k <= 3; k++) {
                final int cap = k;
                Map<String, Set<List<Integer>>> first = run(pass, q -> q.SEARCH(cap));
                Map<String, Set<List<Integer>>> firstAtMost = run(pass, q -> q.WHERE(ATMOST(bound)).SEARCH(cap));
                Map<String, Set<List<Integer>>> firstAtLeast = run(pass, q -> q.WHERE(ATLEAST(bound)).SEARCH(cap));
                for (String g : GAMES) {
                    Set<List<Integer>> every = all.getOrDefault(g, Set.of());
                    Set<List<Integer>> capped = first.getOrDefault(g, Set.of());
                    assertEquals(Math.min(cap, every.size()), capped.size(), g + " SEARCH(" + cap + ")");
                    assertTrue(every.containsAll(capped), g + " SEARCH(" + cap + ")");
                    // a bound and SEARCH: the bound decides on all the answers, SEARCH keeps the first ones
                    assertEquals(every.size() <= bound ? capped : Set.of(), firstAtMost.getOrDefault(g, Set.of()),
                            g + " ATMOST(" + bound + ").SEARCH(" + cap + ")");
                    assertEquals(every.size() >= bound ? capped : Set.of(), firstAtLeast.getOrDefault(g, Set.of()),
                            g + " ATLEAST(" + bound + ").SEARCH(" + cap + ")");
                }
            }
        }
        assertTrue(kept > 0 && dropped > 0);
    }
}
