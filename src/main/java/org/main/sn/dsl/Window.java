package org.main.sn.dsl;

final class Window implements Pattern {
    public final String name;
    public double duration; // null means unspecified
    public double start, end; // optional absolute times (ms)

    public Window(String name) {
        this.name = name;
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

    @Override
    public Pattern within(double duration) {
        this.duration = duration;
        return this;
    }

    @Override
    public void from(String... ids) {

    }

    @Override
    public void search() {}
}
