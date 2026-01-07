package org.main.sn.dsl;

public class TeamEvent extends Event{
    public final Team subject;
    public final Action action;

    public TeamEvent(Team subject, Action action) {
        this.subject = subject;
        this.action = action;
    }

    public static TeamEvent isInZones(Team team, int... zones) {
        return new TeamEvent(team, new Action("IS_IN_ZONES", zones));
    }

    public static TeamEvent isInFormation(Team team, Formation f) {
        return new TeamEvent(team, new Action("FORMATION", f));
    }
}
