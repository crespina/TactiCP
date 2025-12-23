package org.main.sn.dsl;

final class Window {
    public final String name;
    public Long duration; // null means unspecified
    public Long start, end; // optional absolute times (ms)

    public Window(String name) {
        this.name = name;
    }

    public Window duration(long amount) {
        this.duration = amount;
        return this;
    }

    public Window from(long start) {
        this.start = start;
        return this;
    }

    public Window to(long end) {
        this.end = end;
        return this;
    }

    @Override
    public String toString() {
        return "Window(" + name + ")";
    }
}
