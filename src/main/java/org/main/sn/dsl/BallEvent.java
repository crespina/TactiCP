package org.main.sn.dsl;

public class BallEvent extends Event {
    public final Action action;

    public BallEvent(Action action) {
        this.action = action;
    }

    public static BallEvent moveTo(int zone) {
        return new BallEvent(new Action("MOVE_TO", zone));
    }
}
