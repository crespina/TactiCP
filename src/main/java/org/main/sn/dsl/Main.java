package org.main.sn.dsl;

import java.io.IOException;

import static org.main.sn.dsl.Factory.*;

public class Main {

    public static void main(String[] args) throws IOException {
        Player p1 = player("p1");
        Player p2 = player("p2");

        Event e1 = p1.passTo(p2);
        Event e2 = player("p2").passTo(player("p1"));
        Sequence s = sequence("one two", e1, e2);
        Sequence s2 = sequence("one two", player("p1").passTo(player("p2")).within(10),
            player("p2").passTo(player("p1")).within(10));
        Ball b = ball();
        Team left = team("left");
        Team right = team("right");
        left.isInFormation(formation(4, 3, 3));
        Sequence sq = new Sequence("seq", e1, e2).from("train");
        sq.search();
    }
}
