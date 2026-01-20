package org.main.sn.dsl;

public class TeamEvent extends Event{


    public TeamEvent(Action action, Team subject) {
        super(action, subject);
    }

    public static TeamEvent isInZones(Team team, int... zones) {
        return new TeamEvent(new Action("IS_IN_ZONES", zones), team);
    }

    public static TeamEvent isInFormation(Team team, Formation f) {
        return new TeamEvent(new Action("FORMATION", f), team);
    }
}
