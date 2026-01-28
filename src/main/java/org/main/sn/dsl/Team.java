package org.main.sn.dsl;

import java.util.ArrayList;
import java.util.List;

public final class Team extends Entity {

    private final String name;
    public final List<Integer> players = new ArrayList<>();

    public Team(String name, int... ids) {
        super(name);
        if (name == null) throw new IllegalArgumentException("name must not be null");
        this.name = name;
        for (int id : ids) {
            players.add(id);
        }
    }

    public Team (String name) {
        this(name, new int[]{});
    }


    public String name() {
        return name;
    }

    public TeamEvent ISINZONES(int... z) {
        return TeamEvent.isInZones(this, z);
    }

    public TeamEvent isInFormation(Formation f){
        return TeamEvent.isInFormation(this, f);
    }

    @Override
    public String toString() {
        return name;
    }
}

