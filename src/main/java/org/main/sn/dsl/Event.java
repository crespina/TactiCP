package org.main.sn.dsl;

public abstract class Event {
    private final Action action;
    private final Entity subject;
    public int timeStart = -1;
    public int timeEnd = -1;
    public int maxDuration = -1;
    public int minDuration = -1;
    public int xtop = -1;
    public int ytop = -1;
    public int w = -1;
    public int h = -1;
    public int radius = -1;
    public int xcenter = -1;
    public int ycenter = -1;
    public boolean isNegated = false;
    public boolean isMinrange = false;

    public Event(Action action, Entity subject) {
        this.action = action;
        this.subject = subject;
    }

    public Event WHERE(Where... parts) {
        for (Where w : parts) {
            switch (w.kind()) {
                case START -> this.timeStart = w.values()[0];
                case END -> this.timeEnd = w.values()[0];
                case WITHIN -> this.maxDuration = w.values()[0];
                case MIN_DURATION -> this.minDuration = w.values()[0];
                case RADIUS -> {
                    this.xcenter = w.values()[0];
                    this.ycenter = w.values()[1];
                    this.radius = w.values()[2];
                }
                case RECTANGLE -> {
                    this.xtop = w.values()[0];
                    this.ytop = w.values()[1];
                    this.w = w.values()[2];
                    this.h = w.values()[3];
                }
                case ATMOST -> throw new IllegalArgumentException("ATMOST is not supported in Event WHERE clause.");
                case ATLEAST -> throw new IllegalArgumentException("ATLEAST is not supported in Event WHERE clause.");
            }
        }
        return this;
    }

    public Event WITHIN(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Duration must be a non-negative value.");
        }
        this.maxDuration = amount; //in frame
        return this;
    }

    public Event MINRANGE() {
        this.isMinrange = true;
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

    public Event RECTANGLE(int xtop, int ytop, int w, int h) {
        this.xtop = xtop;
        this.ytop = ytop;
        if (w <= 0 || h <= 0) {
            throw new IllegalArgumentException("Width and height must be positive values.");
        }
        this.w = w;
        this.h = h;
        return this;
    }

    public Event not() {
        this.isNegated = true;
        if (this instanceof OrEvent orEvent) {
            for (Event e : orEvent.children()) {
                e.isNegated = !e.isNegated;
            }
        } else if (this instanceof AndEvent andEvent) {
            for (Event e : andEvent.children()) {
                e.isNegated = !e.isNegated;
            }
        }
        return this;
    }

    public Action action() {
        return action;
    }

    public Entity subject() {
        return subject;
    }
}


