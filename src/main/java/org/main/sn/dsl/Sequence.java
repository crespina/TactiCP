package org.main.sn.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Sequence implements Pattern {
    public final List<Event> steps = new ArrayList<>();

    public Sequence(Event... exprs) {
        Collections.addAll(steps, exprs);
    }
    //TODO add within
}
