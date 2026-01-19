package org.main.sn.dsl;

import java.io.IOException;

import static org.main.sn.dsl.Factory.*;

public class Main {

    public static void main(String[] args) throws IOException {
        Player p1 = player("p1");
        Player p2 = player("p2");
        Player p3 = player("p3");
        Player p4 = player("p4", 19);

        Event e1 = p1.passTo(p2);
        Event e3 = not(player("p1").hasBall());
        Event e2 = p1.passTo(p3);

        Event e4 = p4.hasBall();

        //Sequence sq = new Sequence("seq", e1, e2).from("train");
        Sequence sq = new Sequence("seq", e1, e3, e2).from("SNGS-060");
        sq.search();
    }
}
