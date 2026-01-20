package org.main.sn.dsl;

public final class Ball extends Entity {
    private static final Ball INSTANCE = new Ball();

    private Ball() {
        super("ball");
    }

    public static Ball get() {
        return INSTANCE;
    }

    public BallEvent moveTo(int z) {
        return BallEvent.moveTo(z);
    }

    public BallEvent moveTo(int s, int e) {
        return BallEvent.moveTo(s, e);
    }

    @Override
    public String toString() {
        return "BALL";
    }

}