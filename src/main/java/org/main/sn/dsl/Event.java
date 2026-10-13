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
                case MAXDURATION -> this.maxDuration = w.values()[0];
                case MINDURATION -> this.minDuration = w.values()[0];
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


    /**
     * The negation of this event. An atomic event toggles its negation, so NOT(NOT(e)) is e. A group
     * follows De Morgan's laws, the NOT going down to the atomic events: NOT(AND(a, b)) is
     * OR(NOT(a), NOT(b)), and NOT(OR(a, b)) is AND(NOT(a), NOT(b)). A WHERE or a MINRANGE already
     * written on the group is kept on the group that replaces it: as for an atomic event, NOT does
     * not negate it, it applies to the negated event.
     */
    public Event not() {
        if (this instanceof AndEvent and) {
            Event negated = new OrEvent(and.children().stream().map(Event::not).toArray(Event[]::new));
            return negated.withModifiersOf(this);
        }
        if (this instanceof OrEvent or) {
            Event negated = new AndEvent(or.children().stream().map(Event::not).toArray(Event[]::new));
            return negated.withModifiersOf(this);
        }
        this.isNegated = !this.isNegated;
        return this;
    }

    /** Copies the WHERE and the MINRANGE of {@code other} onto this event. */
    private Event withModifiersOf(Event other) {
        timeStartMin = other.timeStartMin;
        timeEndMin = other.timeEndMin;
        timeStartMax = other.timeStartMax;
        timeEndMax = other.timeEndMax;
        maxDuration = other.maxDuration;
        minDuration = other.minDuration;
        xtop = other.xtop;
        ytop = other.ytop;
        w = other.w;
        h = other.h;
        radius = other.radius;
        xcenter = other.xcenter;
        ycenter = other.ycenter;
        isMinrange = other.isMinrange;
        return this;
    }

    public Action action() {
        return action;
    }

    public Entity subject() {
        return subject;
    }
}


