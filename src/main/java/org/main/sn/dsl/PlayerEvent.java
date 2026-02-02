package org.main.sn.dsl;

public final class PlayerEvent extends Event {

    public PlayerEvent(Action action, Player subject) {
        super(action, subject);
    }

    public static PlayerEvent pass(Player from, Player to) {
        return new PlayerEvent(new Action("PASS_TO", to), from);
    }

    public static PlayerEvent moveTo(Player p, int zone) {
        return new PlayerEvent(new Action("MOVE_TO", zone), p);
    }

    public  static PlayerEvent moveTo(Player p, int zone_start, int zone_end) {
        return new PlayerEvent(new Action("MOVE_TO", new int[]{zone_start, zone_end}), p);
    }


}
