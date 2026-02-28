/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.main.sn;


import org.junit.jupiter.api.Test;
import org.main.sn.dsl.*;
import org.main.sn.logic.Result;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.main.sn.dsl.Factory.*;

public class QueryTest {

    @Test
    public void TestBallMoveTo() throws IOException {

        Ball b = BALL();

        //ball move to
        SelectExpr s = SELECT(b.MOVETO(4, 6).WHERE(RADIUS(-20, 10, 35)), b.MOVETO(14, 9).MINRANGE()).FROM("SNGS-061").WHERE(RECTANGLE(-100, 100, 810, 810)); //toutes les passes

        List<Result> results = s.search();
        assertEquals(2, results.size());


        Result r1 = results.get(0);
        assertEquals("SNGS-061", r1.instance);
        List<Result.ResultEvent> events = r1.events;
        assertEquals(2, events.size());

        Result.ResultEvent event1 = events.getFirst();
        assertEquals("BALL_MOVE_TO", event1.type);
        Result.BallMoveEvent ballMoveEvent = (Result.BallMoveEvent) event1;
        assertEquals(47, ballMoveEvent.interval.start);
        assertEquals(247, ballMoveEvent.interval.end);

        Result.ResultEvent event2 = events.get(1);
        assertEquals("BALL_MOVE_TO", event2.type);
        Result.BallMoveEvent ballMoveEvent2 = (Result.BallMoveEvent) event2;
        assertEquals(456, ballMoveEvent2.interval.start);
        assertEquals(458, ballMoveEvent2.interval.end);

        //NOT
        SelectExpr snot = SELECT(NOT(b.MOVETO(4, 6))).FROM("SNGS-061").WHERE(RADIUS(0, 0, 400)); //toutes les passes

        List<Result> resultsnot = snot.search();
        assertEquals(2, resultsnot.size());


        Result r1not = resultsnot.get(0);
        assertEquals("SNGS-061", r1not.instance);
        List<Result.ResultEvent> eventsnot = r1not.events;
        assertEquals(1, eventsnot.size());

        Result.ResultEvent event1not = eventsnot.getFirst();
        assertEquals("BALL_MOVE_TO", event1not.type);
        Result.BallMoveEvent ballMoveEventnot = (Result.BallMoveEvent) event1not;
        assertEquals(0, ballMoveEventnot.interval.start);
        assertEquals(47, ballMoveEventnot.interval.end);

        Result.ResultEvent event2not = resultsnot.get(1).events.getFirst();
        assertEquals("BALL_MOVE_TO", event2not.type);
        Result.BallMoveEvent ballMoveEventnot2 = (Result.BallMoveEvent) event2not;
        assertEquals(247, ballMoveEventnot2.interval.start);
        assertEquals(750, ballMoveEventnot2.interval.end);


    }

    @Test
    public void TestPassTo() throws IOException {

        Player pwc1 = PLAYER("pwc1");
        Player pwc2 = PLAYER("pwc2");

        //PASS BETWEEN TWO WILDCARDS
        Event e1 = pwc1.PASSTO(pwc2).MINRANGE();
        SelectExpr s1 = SELECT(e1).FROM("SNGS-060, SNGS-061").WHERE(RECTANGLE(-60, 20, 65, 40)); //toutes les passes

        List<Result> results = s1.search();
        assertEquals(5, results.size());


        Result r1 = results.get(0);
        assertEquals("SNGS-061", r1.instance);
        List<Result.ResultEvent> events = r1.events;
        assertEquals(1, events.size());
        Result.ResultEvent event = events.getFirst();
        assertEquals("PASS_TO", event.type);
        Result.PassEvent passEvent = (Result.PassEvent) event;
        assertEquals(11, passEvent.passerId);
        assertEquals(14, passEvent.receiverId);
        assertEquals(84, passEvent.interval.start);
        assertEquals(117, passEvent.interval.end);
        assertEquals(33, passEvent.interval.length);

        Result.PassEvent event2 = (Result.PassEvent) results.get(1).events.getFirst();
        assertEquals(14, event2.passerId);
        assertEquals(13, event2.receiverId);
        assertEquals(139, event2.interval.start);
        assertEquals(164, event2.interval.end);

        Result.PassEvent event4 = (Result.PassEvent) results.get(2).events.getFirst();
        assertEquals(11, event4.passerId);
        assertEquals(16, event4.receiverId);
        assertEquals(21, event4.interval.start);
        assertEquals(44, event4.interval.end);

        Result.PassEvent event5 = (Result.PassEvent) results.get(3).events.getFirst();
        assertEquals(16, event5.passerId);
        assertEquals(19, event5.receiverId);
        assertEquals(117, event5.interval.start);
        assertEquals(136, event5.interval.end);

        Result.PassEvent event6 = (Result.PassEvent) results.get(4).events.getFirst();
        assertEquals(19, event6.passerId);
        assertEquals(22, event6.receiverId);
        assertEquals(142, event6.interval.start);
        assertEquals(182, event6.interval.end);


        //PASS BETWEEN TWO IDS
        Player p11 = PLAYER("p11", 11);
        Player p14 = PLAYER("p14", 14);
        SelectExpr s2 = SELECT(p11.PASSTO(p14).MINRANGE()).FROM("SNGS-060, SNGS-061").WHERE(RADIUS(-20, -10, 15));
        List<Result> results2 = s2.search();
        assertEquals(1, results2.size());

        Result.PassEvent event8 = (Result.PassEvent) results2.get(0).events.getFirst();
        assertEquals(11, event8.passerId);
        assertEquals(14, event8.receiverId);
        assertEquals(84, event8.interval.start);
        assertEquals(117, event8.interval.end);


        //NOT PASS BETWEEN TEAM
        Player pleft1 = PLAYER("pleft1", "left");
        Player pleft2 = PLAYER("pleft2", "left");
        SelectExpr s3 = SELECT(NOT(pleft1.PASSTO(pleft2).MINRANGE())).FROM("SNGS-060").SEARCH(1);
        List<Result> results3 = s3.search();
        assertEquals(1, results3.size());

        Result.PassEvent event9 = (Result.PassEvent) results3.get(0).events.getFirst();
        assertEquals(0, event9.interval.start);
        assertEquals(21, event9.interval.end);

    }

    @Test
    public void TestPlayerMoveTo() throws IOException {

        //wild card
        Player pwc3 = PLAYER("pwc3");
        SelectExpr s = SELECT(pwc3.MOVETO(1, 6).MINRANGE().WHERE(RADIUS(-20, 20, 20)), pwc3.MOVETO(6, 7)).FROM("SNGS-061"); // id : 13

        List<Result> results = s.search();
        assertEquals(1, results.size());


        Result r1 = results.get(0);
        assertEquals("SNGS-061", r1.instance);
        List<Result.ResultEvent> events = r1.events;
        assertEquals(2, events.size());

        Result.ResultEvent event = events.getFirst();
        assertEquals("PLAYER_MOVE_TO", event.type);
        Result.PlayerMoveEvent playerMoveEvent = (Result.PlayerMoveEvent) event;
        assertEquals(13, playerMoveEvent.playerId);
        assertEquals(178, playerMoveEvent.interval.start);
        assertEquals(180, playerMoveEvent.interval.end);

        Result.ResultEvent event2 = events.get(1);
        assertEquals("PLAYER_MOVE_TO", event2.type);
        Result.PlayerMoveEvent playerMoveEvent2 = (Result.PlayerMoveEvent) event2;
        assertEquals(13, playerMoveEvent2.playerId);
        assertEquals(179, playerMoveEvent2.interval.start);
        assertEquals(411, playerMoveEvent2.interval.end);


        //id
        Player p24 = PLAYER("p24", 24);
        SelectExpr s2 = SELECT(p24.MOVETO(10, 3)).FROM("SNGS-061").WHERE(RECTANGLE(0, -5, 28, 28));
        List<Result> results2 = s2.search();
        assertEquals(1, results2.size());
        Result rid = results2.get(0);
        assertEquals("SNGS-061", rid.instance);
        List<Result.ResultEvent> idev = rid.events;
        assertEquals(1, idev.size());
        Result.ResultEvent eventId = idev.getFirst();
        assertEquals("PLAYER_MOVE_TO", eventId.type);
        Result.PlayerMoveEvent playerMoveEventId = (Result.PlayerMoveEvent) eventId;
        assertEquals(24, playerMoveEventId.playerId);
        assertEquals(189, playerMoveEventId.interval.start);
        assertEquals(327, playerMoveEventId.interval.end);


        //team
        Player pleft3 = PLAYER("pleft3", "left");
        SelectExpr s3 = SELECT(NOT(pleft3.MOVETO(10, 3))).FROM("SNGS-061");
        List<Result> results3 = s3.search();
        assertEquals(2, results3.size());
        List<Result.ResultEvent> notevnets = results3.get(0).events;
        Result.PlayerMoveEvent notev1 = (Result.PlayerMoveEvent) notevnets.getFirst();
        assertEquals(24, notev1.playerId);
        assertEquals(0, notev1.interval.start);
        assertEquals(189, notev1.interval.end);
        List<Result.ResultEvent> notevnets2 = results3.get(1).events;
        Result.PlayerMoveEvent notev2 = (Result.PlayerMoveEvent) notevnets2.getFirst();
        assertEquals(24, notev2.playerId);
        assertEquals(328, notev2.interval.start);
        assertEquals(750, notev2.interval.end);

    }

    @Test
    public void TestTeamMoveTo() throws IOException {

        Player pwc1 = PLAYER("pwc18");
        Player pleft = PLAYER("pleft", "left");
        Player p1 = PLAYER("p1", 1);


        Team t2 = TEAM("t2", pwc1);
        SELECT(pwc1.MOVETO(11,10).MINRANGE()).FROM("SNGS-060").searchAndPrint(); //toutes les passes
        //SELECT(t2.MOVETO(11, 10, 1)).FROM("SNGS-060").searchAndPrint(); //toutes les passes


        Team t1 = TEAM("t1", pwc1, pleft, p1);

        SelectExpr s1 = SELECT(t1.MOVETO(11, 10, 3)).FROM("SNGS-060"); //toutes les passes
        //List<Result> results = s1.search();
        //assertEquals(1, results.size());

        //.WHERE(RECTANGLE(-35, -20, 60, 20)
    }


}
