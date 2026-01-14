package org.main.sn.dsl;

public abstract class Entity {
    String name;

    public Entity(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }
}
