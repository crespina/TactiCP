package org.main.sn.dsl;

public final class PlayerEvent extends Event {

    public PlayerEvent(Player subject, Action action) {
        super(action, subject);
    }

    public static PlayerEvent pass(Player from, Player to) {
        return new PlayerEvent(from, new Action("PASS_TO", to));
    }

    public static PlayerEvent hasBall(Player p) {
        return new PlayerEvent(p, new Action("HAS_BALL"));
    }

    public static PlayerEvent moveTo(Player p, int zone) {
        return new PlayerEvent(p, new Action("MOVE_TO", zone));
    }

    public  static PlayerEvent moveTo(Player p, int zone_start, int zone_end) {
        return new PlayerEvent(p, new Action("MOVE_TO", new int[]{zone_start, zone_end}));
    }


}
