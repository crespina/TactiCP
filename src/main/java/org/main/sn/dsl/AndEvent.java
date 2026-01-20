package org.main.sn.dsl;
import java.util.Arrays;
import java.util.List;

public final class AndEvent extends Event {
    private final List<Event> children;
    public AndEvent(Event... children) { super(null,null); this.children = Arrays.asList(children); }
    public List<Event> children() { return children; }
    @Override public String toString() { return "AND" + children; }
}

