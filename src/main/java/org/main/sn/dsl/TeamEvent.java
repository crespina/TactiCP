package org.main.sn.dsl;

public class TeamEvent extends Event{
    private final Action action;
    private final Team subject;


    public TeamEvent(Action action, Team subject) {
        super(action, subject);
        this.action = action;
        this.subject = subject;
    }

    public static TeamEvent isInZones(Team team, int... zones) {
        return new TeamEvent(new Action("IS_IN_ZONES", zones), team);
    }

    public static TeamEvent isInFormation(Team team, Formation f) {
        return new TeamEvent(new Action("FORMATION", f), team);
    }
}
