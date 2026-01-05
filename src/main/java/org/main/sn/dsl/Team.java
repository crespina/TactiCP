package org.main.sn.dsl;

public final class Team extends Entity {;
    public static final Team LEFT  = new Team("left");
    public static final Team RIGHT = new Team("right");

    private final String name;

    private Team(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public static Team of(String name) {
        return switch (name.toLowerCase()) {
            case "left"  -> LEFT;
            case "right" -> RIGHT;
            default -> throw new IllegalArgumentException(
                    "Invalid team name: " + name + " (expected 'left' or 'right')"
            );
        };
    }

    public TeamEvent isInZone(int z) {
        return TeamEvent.isInZone(this, z);
    }

    public TeamEvent isInFormation(Formation f){
        return TeamEvent.isInFormation(this, f);
    }

    @Override
    public String toString() {
        return name;
    }
}

