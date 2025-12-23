package org.main.sn.dsl;

final class Ball extends Entity {
    String name = "ball";
    private static final Ball INSTANCE = new Ball();

    private Ball() {
    }

    public static Ball get() {
        return INSTANCE;
    }

    public BallEvent moveTo(int z) {
        return BallEvent.moveTo(z);
    }

    @Override
    public String toString() {
        return "BALL";
    }

}