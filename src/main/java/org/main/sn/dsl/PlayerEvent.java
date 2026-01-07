package org.main.sn.dsl;

public final class PlayerEvent extends Event {
    public final Player subject;
    public final Action action;

    public PlayerEvent(Player subject, Action action) {
        this.subject = subject;
        this.action = action;
    }

    public static PlayerEvent pass(Player from, Player to) {
        return new PlayerEvent(from, new Action("PASS_TO", to));
    }

    public static Event pass(Player from, Player to, int amount) {
        PlayerEvent e = pass(from, to);
        return e.within(amount);
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
