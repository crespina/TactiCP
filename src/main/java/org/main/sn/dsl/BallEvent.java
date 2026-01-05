package org.main.sn.dsl;

public class BallEvent extends Event {
    public final Action action;
    public final Entity subject;

    public BallEvent(Action action) {
        this.action = action;
        this.subject = Ball.get();
    }

    public static BallEvent moveTo(int zone) {
        return new BallEvent(new Action("BALL_MOVE_TO", zone));
    }
}
