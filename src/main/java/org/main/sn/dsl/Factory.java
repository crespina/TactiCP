package org.main.sn.dsl;

import java.util.HashMap;
import java.util.Map;

public class Factory {

    private static final Map<String, Entity> IDENTIFIERS = new HashMap<>();

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

    public static Player player(String name, int id, String team) {
        return (Player) canonical(new Player(name, id, team));
    }

    public static Player player(String name, int id) {
        return (Player) canonical(new Player(name, id));
    }

    public static Player player(String name, String team) {
        return (Player) canonical(new Player(name, team));
    }

    public static Player player(String name) {
        Entity p = new Player(name);
        return (Player) canonical(p);
    }

    public static Ball ball() {
        return Ball.get();
    }

    public static Team team(String name) {
        return new Team(name);
    }

    public static Team team(String name, int... ids) {
        return new Team(name, ids);
    }

//    public static Formation formation(int... lines) {
//        return Formation.of(lines);
//    }

    public static Event not(Event event) {
        return event.not();
    }

    public static Event or(Event... events) {
        return new OrEvent(events);
    }

    public static Event and(Event... events) {
        return new AndEvent(events);
    }


    public static Sequence sequence(String name, Event... exprs) {
        return new Sequence(name, exprs);
    }

    public static void count(Sequence seq) {
        seq.count();
    }

    public static void atLeast(Sequence seq, int n) {
        seq.atLeast(n);
    }

    public static void  atMost(Sequence seq, int n) {
        seq.atMost(n);
    }

}
