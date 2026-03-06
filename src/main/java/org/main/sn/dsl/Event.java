package org.main.sn.dsl;

public abstract class Event {
    private final Action action;
    private final Entity subject;
    public int timeStartMin = -1;
    public int timeEndMin = -1;
    public int timeStartMax = -1;
    public int timeEndMax = -1;
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
                case STARTMIN -> this.timeStartMin = w.values()[0];
                case STARTMAX -> this.timeStartMax = w.values()[0];
                case ENDMIN -> this.timeEndMin = w.values()[0];
                case ENDMAX -> this.timeEndMax = w.values()[0];
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

    public Event MINRANGE() {
        this.isMinrange = true;
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


