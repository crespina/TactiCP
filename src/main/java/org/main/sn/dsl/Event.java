package org.main.sn.dsl;

abstract class Event {
    public double timeStart = -1;
    public double timeEnd = -1;
    public double duration = -1;

    public Event within(double amount) {
        this.duration = amount;
        return this;
    }

    public Event start(double time) {
        this.timeStart = time;
        return this;
    }

    public Event end(double time) {
        this.timeEnd = time;
        return this;
    }
}


