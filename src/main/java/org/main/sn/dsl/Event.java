package org.main.sn.dsl;

public abstract class Event {
    private final Action action;
    private final Entity subject;
    public int timeStart = -1;
    public int timeEnd = -1;
    public int duration = -1;
    public int xtop = -1;
    public int ytop = -1;
    public int w = -1;
    public int h = -1;
    public int radius = -1;
    public int xcenter = -1;
    public int ycenter = -1;

    public Event(Action action, Entity subject) {
        this.action = action;
        this.subject = subject;
    }

    public Event within(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Duration must be a non-negative value.");
        }
        this.duration = amount; //in frame
        return this;
    }

    public Event start(int time) {
        if (time < 0) {
            throw new IllegalArgumentException("Start time must be a non-negative value.");
        }
        this.timeStart = time; //in frame
        return this;
    }

    public Event end(int time) {
        if (time < 0) {
            throw new IllegalArgumentException("End time must be a non-negative value.");
        }
        this.timeEnd = time; //in frame
        return this;
    }

    public Event radius(int radius, int xcenter, int ycenter) {
        this.radius = radius;
        if (radius <= 0) {
            throw new IllegalArgumentException("Radius must be a positive value.");
        }
        this.xcenter = xcenter;
        this.ycenter = ycenter;
        return this;
    }

    public Event rectangle(int xtop, int ytop, int w, int h) {
        this.xtop = xtop;
        this.ytop = ytop;
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width and height must be positive values.");
        }
        this.w = w;
        this.h = h;
        return this;
    }

    public Action action() {
        return action;
    }

    public Entity subject() {
        return subject;
    }
}


