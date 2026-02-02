package org.main.sn.dsl;

import java.util.Arrays;
import java.util.List;

public class OrEvent extends Event {
    private final List<Event> children;

    public OrEvent(Event... events) {
        super(null,null);
        this.children = Arrays.asList(events);
        if (this.isNegated){
            for (Event e : children) {
                e.isNegated = !e.isNegated;
            }
        }
    }

    public List<Event> children() {
        return children;
    }

    @Override
    public String toString() {
        return "OR" + children;
    }
}

