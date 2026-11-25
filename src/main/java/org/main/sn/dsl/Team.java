package org.main.sn.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class Team extends Entity {

    private final String name;
    public final List<Player> players = new ArrayList<>();

    /**
     * The only two names that denote a whole side on their own. Everything else is
     * a custom team and must be given its members explicitly. {@code Query} expands
     * a side to its roster by comparing against these literals, so the spelling is
     * significant — {@code "LEFT"} is not a side.
     */
    public static boolean isSide(String name) {
        return "left".equals(name) || "right".equals(name);
    }

    public Team(String name, Player... members) {
        super(name);
        if (name == null) throw new IllegalArgumentException("name must not be null");
        if (members.length == 0 && !isSide(name)) {
            throw new IllegalArgumentException("TEAM(\"" + name + "\") has no members. Only the"
                    + " lowercase names \"left\" and \"right\" denote a whole side by themselves;"
                    + " a custom team must list its members, e.g. TEAM(\"" + name + "\", 8, 9, 10)"
                    + " or TEAM(\"" + name + "\", PLAYER(\"a\", 8), PLAYER(\"b\", 9)).");
        }
        this.name = name;
        this.players.addAll(Arrays.asList(members));
    }

    public Team(String name, Set<Integer> ids) {
        super(name);
        if (name == null) throw new IllegalArgumentException("name must not be null");
        this.name = name;
        this.players.addAll(ids.stream().map(id -> new Player("Player" + id, id)).toList());
    }


    public String name() {
        return name;
    }

    public List<Player> players() {return players;}

    public TeamEvent MOVETO(int startZone, int endZone, int k) {
        return TeamEvent.moveTo(this, new int[]{startZone, endZone, k});
    }

    @Override
    public String toString() {
        return name;
    }
}

