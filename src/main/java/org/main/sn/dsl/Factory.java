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

    public static Team TEAM(String name) {
        return new Team(name);
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

//    public static Formation formation(int... lines) {
//        return Formation.of(lines);
//    }

    public static Event NOT(Event event) {
        return event.not();
    }

    public static Event OR(Event... events) {
        return new OrEvent(events);
    }

    public static Event AND(Event... events) {
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
        return new PlayerEvent(new Action("POSITION", zones), p);
    }

    public static Event POSITION(Team t, K k, int... zones) {
        return new TeamEvent(new Action("POSITION", java.util.stream.IntStream.concat(java.util.Arrays.stream(zones), java.util.stream.IntStream.of(k.value)).toArray()), t);
    }

    public static Event POSITION(Team t, int... zones) {
        return new TeamEvent(new Action("POSITION", java.util.stream.IntStream.concat(java.util.Arrays.stream(zones), java.util.stream.IntStream.of(1)).toArray()), t);
    }

    public static Event POSITION(Ball b, int... zones) {
            return new BallEvent(new Action("POSITION", zones));
    }

    public static Where START(int frame) {
        return Where.of(Where.Kind.START, frame);
    }

    public static Where END(int frame) {
        return Where.of(Where.Kind.END, frame);
    }

    public static Where WITHIN(int frames) {
        return Where.of(Where.Kind.WITHIN, frames);
    }

    public static Where RECTANGLE(int x1, int y1, int x2, int y2) {
        return Where.of(Where.Kind.RECTANGLE, x1, y1, x2, y2);
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


