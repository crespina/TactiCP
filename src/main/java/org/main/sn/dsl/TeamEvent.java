package org.main.sn.dsl;

public class TeamEvent {
    public final Action action;

    public TeamEvent(Action action) {
        this.action = action;
    }

    public static TeamEvent isInZone(int zone) {
        return new TeamEvent(new Action("IS_IN_ZONE", zone));
    }

    public static TeamEvent isInFormation(Formation f) {
        return new TeamEvent(new Action("FORMATION", f));
    }
}
