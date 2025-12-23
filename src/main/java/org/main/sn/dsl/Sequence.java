package org.main.sn.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class Sequence implements Pattern {
    public final List<Event> steps = new ArrayList<>();
    public double duration;

    public Sequence(Event... exprs) {
        Collections.addAll(steps, exprs);
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
    public void search() {

    }


}
