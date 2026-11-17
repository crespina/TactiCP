package org.main.sn.dsl;

public class TeamEvent extends Event{


    public TeamEvent(Action action, Team subject) {
        super(action, subject);
    }

    public static TeamEvent moveTo(Team team, int[] zonesAndK) {
        PlayerEvent.checkZonesDiffer(zonesAndK[0], zonesAndK[1]);
        if (zonesAndK[2] < 1) {
            throw new IllegalArgumentException("TEAM MOVETO needs k >= 1, got " + zonesAndK[2] + ".");
        }
        return new TeamEvent(new Action("TEAM_MOVE_TO",  zonesAndK), team);
    }
}
