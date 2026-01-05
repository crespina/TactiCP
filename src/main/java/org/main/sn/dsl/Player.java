package org.main.sn.dsl;

import java.util.Optional;

public final class Player extends Entity {
    public final String name;
    private Optional<Integer> id;
    private Optional<String> team;

    public Player(String name) {
        this(name, null, null);
    }

    public Player(String name, int id) {
        this(name, id, null);
    }

    public Player(String name, String team) {
        this(name, null, team);
    }

    public Player(String name, Integer id, String team) {
        if (name == null) throw new IllegalArgumentException("name must not be null");
        this.name = name;
        this.id = Optional.ofNullable(id);
        this.team = Optional.ofNullable(team);
    }

    public Player id(Integer id) {
        return new Player(this.name, id, this.team.orElse(null));
    }

    public Player team(String team) {
        return new Player(this.name, this.id.orElse(null), team);
    }

    //getters
    public Integer id() {
        return id.orElse(null);
    }

    public String team() {
        return team.orElse(null);
    }

    //setters
    void setId(Integer id) {
        if (this.id.isEmpty() && id != null)
            this.id = Optional.of(id);
    }

    void setTeam(String team) {
        if (this.team.isEmpty() && team != null)
            this.team = Optional.of(team);
    }


    //equals
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Player)) return false;
        Player other = (Player) o;
        return name.equals(other.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }


    @Override
    public String toString() {
        return name + " #" + id + "(" + team + ")";
    }

    public PlayerEvent passTo(Player target) {
        return PlayerEvent.pass(this, target);
    }

    public PlayerEvent hasBall() {
        return PlayerEvent.hasBall(this);
    }

    public PlayerEvent moveTo(int z) {
        return PlayerEvent.moveTo(this, z);
    }
}