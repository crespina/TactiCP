package org.main.sn.dsl;

public final class Action {
    public final String name;
    public final Object payload;

    public Action(String n) {
        this(n, null);
    }

    public Action(String n, Object payload) {
        this.name = n;
        this.payload = payload;
    }

    @Override
    public String toString() {
        return name + (payload == null ? "" : "(" + payload + ")");
    }
}
