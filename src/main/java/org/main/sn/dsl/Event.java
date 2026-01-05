package org.main.sn.dsl;

public abstract class Event {
    public Action action;
    public Entity subject;
    public int timeStart = -1; //in frame
    public int timeEnd = -1; //in frame
    public int duration = -1; //in frame

    public Event within(int amount) {
        this.duration = amount;
        return this;
    }

    public Event start(int time) {
        this.timeStart = time;
        return this;
    }

    public Event end(int time) {
        this.timeEnd = time;
        return this;
    }
}


