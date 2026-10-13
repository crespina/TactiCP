package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Event;
import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;
import org.main.sn.dsl.Where;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.main.sn.dsl.Factory.*;

/**
 * WHERE and MINRANGE written on an AND or an OR. A temporal WHERE bounds the group's interval: the
 * intersection of an AND, the chosen branch of an OR. A spatial WHERE and a MINRANGE apply to each
 * member, as if they were written on every one of them; a spatial WHERE on the whole query applies
 * to each event in the same way.
 */
public class GroupModifiersTest {

    private static final List<String> GAMES = List.of("SNGS-060", "SNGS-061");

    /** One answer: its formatted text without its number, and the interval of its first step. */
    record Answer(String text, int start, int end) {}

    private static Set<Answer> run(SelectExpr query) {
        List<Result> results = query.search();
        Set<Answer> answers = new HashSet<>();
        for (Result r : results) {
            List<String> lines = r.toFormattedStrings();
            Result.ResultEvent first = r.events.getFirst();
            answers.add(new Answer(String.join("\n", lines.subList(1, lines.size())), first.interval.start, first.interval.end));
        }
        assertEquals(results.size(), answers.size(), "duplicate answers");
        Factory.reset();
        return answers;
    }

    private static Set<Answer> run(Event event, String game) throws Exception {
        Factory.reset();
        return run(SELECT(event).FROM(game));
    }

    private static int median(List<Integer> values) {
        List<Integer> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    @Test
    public void temporalWhereOnAGroupBoundsItsInterval() throws Exception {
        Map<String, Supplier<Event>> groups = new LinkedHashMap<>();
        groups.put("AND", () -> AND(POSITION(PLAYER("p", "left"), 1, 2, 3, 4), POSITION(BALL(), 1, 2, 3, 4)));
        groups.put("OR", () -> OR(POSITION(BALL(), 1, 2), POSITION(BALL(), 3, 4)));
        int dropped = 0;
        for (String G : GAMES) {
            for (Map.Entry<String, Supplier<Event>> group : groups.entrySet()) {
                Set<Answer> all = run(group.getValue().get(), G);
                assertFalse(all.isEmpty(), group.getKey() + " " + G);
                // each bound at the median of what it bounds, so that it keeps some answers and drops others
                int start = median(all.stream().map(Answer::start).toList());
                int last = median(all.stream().map(a -> a.end - 1).toList());
                int length = median(all.stream().map(a -> a.end - a.start).toList());
                Map<Where, Predicate<Answer>> bounds = new LinkedHashMap<>();
                bounds.put(STARTMIN(start), a -> a.start >= start);
                bounds.put(STARTMAX(start), a -> a.start <= start);
                bounds.put(ENDMIN(last), a -> a.end - 1 >= last);
                bounds.put(ENDMAX(last), a -> a.end - 1 <= last);
                bounds.put(MINDURATION(length), a -> a.end - a.start >= length);
                bounds.put(MAXDURATION(length), a -> a.end - a.start <= length);
                for (Map.Entry<Where, Predicate<Answer>> bound : bounds.entrySet()) {
                    Set<Answer> expected = new HashSet<>();
                    for (Answer a : all) if (bound.getValue().test(a)) expected.add(a);
                    dropped += all.size() - expected.size();
                    Event e = group.getValue().get().WHERE(bound.getKey());
                    assertEquals(expected, run(e, G), group.getKey() + ".WHERE(" + bound.getKey() + ") " + G);
                }
            }
        }
        assertTrue(dropped > 0);
    }

    @Test
    public void spatialWhereOnAGroupAppliesToEachMember() throws Exception {
        // the first member tests the player, the second one the ball
        Map<String, Supplier<Event[]>> members = new LinkedHashMap<>();
        members.put("AND", () -> new Event[]{POSITION(PLAYER("p", "left"), 1, 2, 3, 4), POSITION(BALL(), 1, 2, 3, 4)});
        members.put("OR", () -> new Event[]{POSITION(PLAYER("p", "left"), 1, 2), POSITION(BALL(), 3, 4)});
        int dropped = 0;
        Map<String, Integer> kept = new HashMap<>();
        for (String G : GAMES) {
            for (Map.Entry<String, Supplier<Event[]>> entry : members.entrySet()) {
                boolean and = entry.getKey().equals("AND");
                Event[] m = entry.getValue().get();
                Set<Answer> without = run(and ? AND(m) : OR(m), G);

                m = entry.getValue().get();
                Set<Answer> onGroup = run((and ? AND(m) : OR(m)).WHERE(RADIUS(0, 0, 25)), G);
                kept.merge(entry.getKey(), onGroup.size(), Integer::sum);

                m = entry.getValue().get();
                for (Event e : m) e.WHERE(RADIUS(0, 0, 25));
                Set<Answer> onEachMember = run(and ? AND(m) : OR(m), G);

                m = entry.getValue().get();
                Factory.reset();
                Set<Answer> onTheQuery = run(SELECT(and ? AND(m) : OR(m)).FROM(G).WHERE(RADIUS(0, 0, 25)));

                String label = entry.getKey() + " " + G;
                assertEquals(onEachMember, onGroup, label);
                assertEquals(onTheQuery, onGroup, label);
                assertTrue(without.containsAll(onGroup), label);
                dropped += without.size() - onGroup.size();
            }
        }
        assertTrue(kept.get("AND") > 0 && kept.get("OR") > 0, kept.toString());
        assertTrue(dropped > 0);
    }

    @Test
    public void minrangeOnAGroupAppliesToEachMember() throws Exception {
        Map<String, Supplier<Event[]>> members = new LinkedHashMap<>();
        members.put("AND", () -> new Event[]{PLAYER("p", "left").MOVETO(1, 4), POSITION(BALL(), 1, 2, 3, 4)});
        members.put("OR", () -> new Event[]{PLAYER("p", "left").MOVETO(1, 4), PLAYER("q", "right").MOVETO(4, 1)});
        int changed = 0;
        Map<String, Integer> found = new HashMap<>();
        for (String G : GAMES) {
            for (Map.Entry<String, Supplier<Event[]>> entry : members.entrySet()) {
                boolean and = entry.getKey().equals("AND");
                Event[] m = entry.getValue().get();
                Set<Answer> without = run(and ? AND(m) : OR(m), G);

                m = entry.getValue().get();
                Set<Answer> onGroup = run((and ? AND(m) : OR(m)).MINRANGE(), G);
                found.merge(entry.getKey(), onGroup.size(), Integer::sum);

                m = entry.getValue().get();
                for (Event e : m) e.MINRANGE();
                Set<Answer> onEachMember = run(and ? AND(m) : OR(m), G);

                assertEquals(onEachMember, onGroup, entry.getKey() + " " + G);
                if (!onGroup.equals(without)) changed++;
            }
        }
        assertTrue(found.get("AND") > 0 && found.get("OR") > 0, found.toString());
        assertTrue(changed > 0);
    }
}
