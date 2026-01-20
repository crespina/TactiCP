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

        Ball ball = ball();
        ball.moveTo(4).within(10);
        ball.moveTo(5,6).within(10);

        Event e4 = p4.hasBall();
        e4.start(10).end(20).rectangle(10,10,2,2).not();
        e3.radius(100,0,0).not();

        //Sequence sq = new Sequence("seq", e1, e2).from("train");
        Sequence sq = new Sequence("seq", or(e1,e2), e3).from("SNGS-060");
        sq.search();
    }
}
