package org.main.sn.dsl;

public final class PlayerEvent extends Event {

    public PlayerEvent(Action action, Player subject) {
        super(action, subject);
    }

    public static PlayerEvent pass(Player from, Player to) {
        return new PlayerEvent(new Action("PASS_TO", to), from);
    }

    public  static PlayerEvent moveTo(Player p, int zone_start, int zone_end) {
        checkZonesDiffer(zone_start, zone_end);
        return new PlayerEvent(new Action("PLAYER_MOVE_TO", new int[]{zone_start, zone_end}), p);
    }

    /** A MOVETO (of the ball, a player or a team) goes from one zone to another one. */
    static void checkZonesDiffer(int zone_start, int zone_end) {
        if (zone_start == zone_end) {
            throw new IllegalArgumentException("Cannot move from one zone to the same zone: MOVETO("
                    + zone_start + ", " + zone_end + ").");
        }
    }


}
