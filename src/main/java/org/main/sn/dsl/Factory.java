package org.main.sn.dsl;

import java.util.HashMap;
import java.util.Map;

public class Factory {

    private static final Map<String, Entity> IDENTIFIERS = new HashMap<>();

    /**
     * Clears the per-name entity cache used to canonicalise identifiers within a
     * single query (the "same name = same player" rule). This cache is static and
     * therefore shared across every query evaluated in the same JVM, so a batch
     * evaluator MUST call this between queries — otherwise a player named {@code "p1"}
     * in one query would inherit the jersey/team assigned to {@code "p1"} in a
     * previously-parsed query, silently corrupting the results.
     */
    public static void reset() {
        IDENTIFIERS.clear();
    }

    private static Entity canonical(Entity e) {
        switch (e) {
            case Player p -> {
                Entity existing = IDENTIFIERS.get(p.name());

                if (existing == null) {
                    IDENTIFIERS.put(p.name(), p);
                    return p;
                }
                Player ex = (Player) existing;

                if (ex.id() == null && p.id() != null) {
                    ex.setId(p.id());
                }
                if (ex.team() == null && p.team() != null) {
                    ex.setTeam(p.team());
                }
                return ex;

            }
            case Team t -> {
                Entity existing = IDENTIFIERS.get(t.name());
                if (existing == null) {
                    IDENTIFIERS.put(t.name(), t);
                    return t;
                }
                return existing;

            }
            case Ball b -> {
                Entity existing = IDENTIFIERS.get(b.name());
                if (existing == null) {
                    IDENTIFIERS.put(b.name(), b);
                    return b;
                }
                return existing;

            }
            case null, default -> throw new IllegalArgumentException("Unknown entity type");
        }

    }

    public static Player PLAYER(String name, int id, String team) {
        return (Player) canonical(new Player(name, id, team));
    }

    public static Player PLAYER(String name, int id) {
        return (Player) canonical(new Player(name, id));
    }

    public static Player PLAYER(String name, String team) {
        return (Player) canonical(new Player(name, team));
    }

    public static Player PLAYER(String name) {
        Entity p = new Player(name);
        return (Player) canonical(p);
    }

    public static Ball BALL() {
        return Ball.get();
    }

    /**
     * A whole side: {@code TEAM("left")} or {@code TEAM("right")}.
     * <p>
     * This exact-arity overload must exist even though both varargs forms below
     * could match a single argument: without it, {@code TEAM("left")} is ambiguous
     * between {@code TEAM(String, Player...)} and {@code TEAM(String, int...)} and
     * fails to resolve in Groovy (and to compile in Java).
     *
     * @throws IllegalArgumentException for any other name — a custom team has no
     *                                  members unless they are listed
     */
    public static Team TEAM(String name) {
        return new Team(name, new Player[0]);
    }

    public static Team TEAM(String name, Player... pls) {
        return new Team(name, pls);
    }

    public static Team TEAM(String name, int... ids) {
        Player[] pls = new Player[ids.length];
        for (int i = 0; i < ids.length; i++) {
            pls[i] = PLAYER("Player" + ids[i], ids[i]);
        }
        return new Team(name, pls);
    }

    public static Event NOT(Event event) {
        return event.not();
    }

    public static Event OR(Event... events) {
        if (events.length < 1)
            throw new IllegalArgumentException("OR requires at least two events");
        return new OrEvent(events);
    }

    public static Event AND(Event... events) {
        if (events.length < 1)
            throw new IllegalArgumentException("AND requires at least two events");
        return new AndEvent(events);
    }


    public static SelectExpr SELECT(Event... exprs) {
        return new SelectExpr(exprs);
    }

    public static Event POSSESSION(Entity entity) {
        if (entity instanceof Player p) {
            return new PlayerEvent(new Action("POSSESSION"), p);
        } else if (entity instanceof Team t){
            return new TeamEvent(new Action("POSSESSION"), t);
        } else {
            throw new IllegalArgumentException("Invalid entity type: " + entity);
        }
    }

    public static Event POSITION(Player p, int... zones) {
        if (zones.length==0){
            throw new IllegalArgumentException("POSITION requires at least one zone, e.g."
                    + " POSITION(BALL(), 4). Zones are integers 1..14.");
        }
        return new PlayerEvent(new Action("POSITION", zones), p);
    }

    public static Event POSITION(Team t, K k, int... zones) {
        if (zones.length==0){
            throw new IllegalArgumentException("POSITION requires at least one zone, e.g."
                    + " POSITION(BALL(), 4). Zones are integers 1..14.");
        }
        return new TeamEvent(new Action("POSITION", java.util.stream.IntStream.concat(java.util.Arrays.stream(zones), java.util.stream.IntStream.of(k.value)).toArray()), t);
    }

    public static Event POSITION(Team t, int... zones) {
        if (zones.length==0){
            throw new IllegalArgumentException("POSITION requires at least one zone, e.g."
                    + " POSITION(BALL(), 4). Zones are integers 1..14.");
        }
        return new TeamEvent(new Action("POSITION", java.util.stream.IntStream.concat(java.util.Arrays.stream(zones), java.util.stream.IntStream.of(1)).toArray()), t);
    }

    public static Event POSITION(Ball b, int... zones) {
        if (zones.length==0){
            throw new IllegalArgumentException("POSITION requires at least one zone, e.g."
                    + " POSITION(BALL(), 4). Zones are integers 1..14.");
        }
        return new BallEvent(new Action("POSITION", zones));
    }

    public static Where STARTMIN(int frame) {
        return Where.of(Where.Kind.STARTMIN, frame);
    }

    public static Where STARTMAX(int frame) {
        return Where.of(Where.Kind.STARTMAX, frame);
    }

    public static Where ENDMIN(int frame) {
        return Where.of(Where.Kind.ENDMIN, frame);
    }

    public static Where ENDMAX(int frame) {
        return Where.of(Where.Kind.ENDMAX, frame);
    }

    public static Where MAXDURATION(int frames) {
        return Where.of(Where.Kind.MAXDURATION, frames);
    }

    public static Where MINDURATION(int frames) {
        return Where.of(Where.Kind.MINDURATION, frames);
    }

    public static Where RECTANGLE(int xtop, int ytop, int w, int h) {
        return Where.of(Where.Kind.RECTANGLE, xtop, ytop, w, h);
    }

    public static Where RADIUS(int x, int y, int r) {
        return Where.of(Where.Kind.RADIUS, x, y, r);
    }

    public static Where ATMOST(int n) {
        return Where.of(Where.Kind.ATMOST, n);
    }

    public static Where ATLEAST(int n) {
        return Where.of(Where.Kind.ATLEAST, n);
    }

    public static final class K {
        public final int value;
        private K(int value) { this.value = value; }
    }

    public static K K(int k) {
        return new K(k);
    }

}


