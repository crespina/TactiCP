package org.main.sn.dsl;

import java.util.Arrays;
import java.util.List;

public class OrEvent extends Event {
    private final List<Event> alts;

    public OrEvent(Event... events) {
        super(null,null);
        this.alts = Arrays.asList(events);
    }

    public List<Event> alternatives() {
        return alts;
    }

    @Override
    public String toString() {
        return "OR" + alts;
    }
}

