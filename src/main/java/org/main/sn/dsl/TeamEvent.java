package org.main.sn.dsl;

public class TeamEvent extends Event{


    public TeamEvent(Action action, Team subject) {
        super(action, subject);
    }

    public static TeamEvent moveTo(Team team, int startZone, int endZone) {
        return new TeamEvent(new Action("TEAM_MOVE_TO",  new int[]{startZone, endZone}), team);
    }
}
