package org.main.sn.dsl;

public final class Player extends Entity {
    public final String name;
    private Integer id;
    private String team;

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
        super(name);
        if (name == null) throw new IllegalArgumentException("name must not be null");
        this.name = name;
        this.id = id;
        this.team = team;
    }

    public Player id(Integer id) {
        return new Player(this.name, id, this.team);
    }

    public Player team(String team) {
        return new Player(this.name, this.id, team);
    }

    //getters
    public Integer id() {
        return id;
    }

    public String team() {
        return team;
    }

    //setters
    void setId(Integer id) {
        if (this.id == null && id != null)
            this.id = id;
    }

    void setTeam(String team) {
        if (this.team == null && team != null)
            this.team = team;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Player other)) return false;
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

    public PlayerEvent moveTo(int zone_start, int zone_end) {
        return PlayerEvent.moveTo(this, zone_start, zone_end);
    }
}
