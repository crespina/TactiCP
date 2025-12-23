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
        } else {
            //TODO other entities
            return e;
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


}
