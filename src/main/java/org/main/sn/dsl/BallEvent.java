package org.main.sn.dsl;

public class BallEvent extends Event {

    public BallEvent(Action action) {
        super(action, Ball.get());
    }

    public static BallEvent moveTo(int zone_end) {
        return new BallEvent(new Action("BALL_MOVE_TO", zone_end));
    }

    public static BallEvent moveTo(int zone_start, int zone_end) {
        return new BallEvent(new Action("BALL_MOVE_TO", new int[]{zone_start, zone_end}));
    }
}
