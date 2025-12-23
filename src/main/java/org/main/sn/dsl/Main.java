package org.main.sn.dsl;

import static org.main.sn.dsl.Factory.*;

public class Main {

    public static void main(String[] args) {
        Player p1 = player("p1");
        Event e1 = p1.passTo(player("p2"));
        Event e2 = player("p2").passTo(player("p1"));
        Sequence s = sequence(e1, e2);
        Ball b = ball();
        Team left = team("left");
        Team right = team("right");
        left.isInFormation(formation(4,3,4));
    }
}
