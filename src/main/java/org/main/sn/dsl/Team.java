package org.main.sn.dsl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public final class Team extends Entity {

    private final String name;
    public final List<Player> players = new ArrayList<>();

    public Team(String name, Player... members) {
        super(name);
        if (name == null) throw new IllegalArgumentException("name must not be null");
        this.name = name;
        this.players.addAll(Arrays.asList(members));
    }

    public Team (String name) {
        this(name, new Player[]{});
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

