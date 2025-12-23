package org.main.sn.dsl;

import java.util.HashMap;
import java.util.Map;

public class Factory {

    private static final Map<String, Entity> IDENTIFIERS = new HashMap<>();

    private static Entity canonical(Entity e) {
        if (e instanceof Player p) {
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

        } else if (e instanceof Team t) {
            Entity existing = IDENTIFIERS.get(t.name());
            if (existing == null) {
                IDENTIFIERS.put(t.name(), t);
                return t;
            }
            return existing;

        } else if (e instanceof Ball b) {
            Entity existing = IDENTIFIERS.get(b.name());
            if (existing == null) {
                IDENTIFIERS.put(b.name(), b);
                return b;
            }
            return existing;

        } else {
            throw new IllegalArgumentException("Unknown entity type");
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
        return Team.of(name);
    }

    public static Formation formation(int... lines) {
        return Formation.of(lines);
    }


    public static Sequence sequence(Event... exprs) {
        return new Sequence(exprs);
    }


}
