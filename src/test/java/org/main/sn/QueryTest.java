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
        SelectExpr s = SELECT(b.MOVETO(4, 6).WHERE(RADIUS(-20, 10, 35)), b.MOVETO(14, 9).MINRANGE()).FROM("SNGS-061").WHERE(RECTANGLE(-100,100,810,810)); //toutes les passes

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

        Result.BallMoveEvent ballMoveEvent22 = (Result.BallMoveEvent) results.get(1).events.get(1);
        assertEquals(510, ballMoveEvent22.interval.start);
        assertEquals(512, ballMoveEvent22.interval.end);

        //NOT
        SelectExpr snot = SELECT(NOT(b.MOVETO(4, 6))).WHERE(RADIUS(0, 0, 400)).FROM("SNGS-061"); //toutes les passes

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

        //NOT MINRANGED
        Event e4 = pwc1.PASSTO(pwc2);
        SelectExpr s4 = SELECT(e4).FROM("SNGS-060, SNGS-061");

        List<Result> results4 = s4.search();
        assertEquals(7, results4.size());

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

        Player pwc1 = PLAYER("pwc1");
        Player pleft = PLAYER("pleft", "left");
        Player p1 = PLAYER("p1", 1);

        Team t1 = TEAM("t1", pwc1, pleft, p1);

        SelectExpr s1 = SELECT(t1.MOVETO(11, 10, 3)).FROM("SNGS-060").WHERE(RECTANGLE(-20,-20,40,20)); //toutes les passes
        List<Result> results = s1.search();
        assertEquals(10, results.size());
        List<Result.ResultEvent> events = results.getFirst().events;

        Result.ResultEvent event0 = events.getFirst();
        assertEquals("TEAM_MOVE_TO", event0.type);
        Result.TeamMoveEvent teamMoveEvent = (Result.TeamMoveEvent) event0;
        assertEquals(List.of(4,2,1), teamMoveEvent.playerIds);
        assertEquals(638, teamMoveEvent.interval.start);
        assertEquals(657, teamMoveEvent.interval.end);

        Result.TeamMoveEvent event1 = (Result.TeamMoveEvent) results.get(1).events.get(0);
        assertEquals(List.of(7,2,1), event1.playerIds);
        assertEquals(291, event1.interval.start);
        assertEquals(364, event1.interval.end);

        Result.TeamMoveEvent event2 = (Result.TeamMoveEvent) results.get(2).events.get(0);
        assertEquals(List.of(8,2,1), event2.playerIds);
        assertEquals(277, event2.interval.start);
        assertEquals(368, event2.interval.end);

        Result.TeamMoveEvent event3 = (Result.TeamMoveEvent) results.get(3).events.get(0);
        assertEquals(List.of(8,2,1), event3.playerIds);
        assertEquals(427, event3.interval.start);
        assertEquals(657, event3.interval.end);

        Result.TeamMoveEvent event4 = (Result.TeamMoveEvent) results.get(4).events.get(0);
        assertEquals(List.of(23,2,1), event4.playerIds);
        assertEquals(277, event4.interval.start);
        assertEquals(368, event4.interval.end);

        Result.TeamMoveEvent event5 = (Result.TeamMoveEvent) results.get(5).events.get(0);
        assertEquals(List.of(4,16,1), event5.playerIds);
        assertEquals(638, event5.interval.start);
        assertEquals(684, event5.interval.end);

        Result.TeamMoveEvent event6 = (Result.TeamMoveEvent) results.get(6).events.get(0);
        assertEquals(List.of(2,18,1), event6.playerIds);
        assertEquals(307, event6.interval.start);
        assertEquals(317, event6.interval.end);

        Result.TeamMoveEvent event7 = (Result.TeamMoveEvent) results.get(7).events.get(0);
        assertEquals(List.of(7,18,1), event7.playerIds);
        assertEquals(307, event7.interval.start);
        assertEquals(317, event7.interval.end);

        Result.TeamMoveEvent event8 = (Result.TeamMoveEvent) results.get(8).events.get(0);
        assertEquals(List.of(8,18,1), event8.playerIds);
        assertEquals(307, event8.interval.start);
        assertEquals(317, event8.interval.end);

        Result.TeamMoveEvent event9 = (Result.TeamMoveEvent) results.get(9).events.get(0);
        assertEquals(List.of(23,18,1), event9.playerIds);
        assertEquals(307, event9.interval.start);
        assertEquals(317, event9.interval.end);

        //NOT
        SelectExpr snot = SELECT(NOT(t1.MOVETO(11, 10, 3))).FROM("SNGS-060").WHERE(RADIUS(-20,-20,40)).SEARCH(1); //toutes les passes
        List<Result> resultsnot = snot.search();
        assertEquals(1, resultsnot.size());
        List<Result.ResultEvent> eventsnot = resultsnot.getFirst().events;

        Result.ResultEvent eventnot0 = eventsnot.getFirst();
        assertEquals("TEAM_MOVE_TO", eventnot0.type);
        Result.TeamMoveEvent teamMoveEventnot = (Result.TeamMoveEvent) eventnot0;
        assertEquals(List.of(4,2,1), teamMoveEventnot.playerIds);
        assertEquals(0, teamMoveEventnot.interval.start);
        assertEquals(256, teamMoveEventnot.interval.end);
    }

    @Test
    public void TestPosition() throws IOException {

        //BALL POSITION
        Ball b = BALL();
        SelectExpr s1 = SELECT(POSITION(b, 1,4)).FROM("SNGS-060").WHERE(RADIUS(-10,0,30));
        List<Result> results1 = s1.search();
        assertEquals(3, results1.size());
        Result r1 = results1.get(0);
        assertEquals("SNGS-060", r1.instance);
        List<Result.ResultEvent> events1 = r1.events;
        assertEquals(1, events1.size());
        Result.ResultEvent event1 = events1.getFirst();
        assertEquals("POSITION", event1.type);
        Result.PositionEvent positionEvent1 = (Result.PositionEvent) event1;
        assertEquals(1, positionEvent1.interval.start);
        assertEquals(156, positionEvent1.interval.end);

        Result r2 = results1.get(1);
        List<Result.ResultEvent> events2 = r2.events;
        Result.ResultEvent event2 = events2.getFirst();
        Result.PositionEvent positionEvent2 = (Result.PositionEvent) event2;
        assertEquals(277, positionEvent2.interval.start);
        assertEquals(306, positionEvent2.interval.end);

        Result r3 = results1.get(2);
        List<Result.ResultEvent> events3 = r3.events;
        Result.ResultEvent event3 = events3.getFirst();
        Result.PositionEvent positionEvent3 = (Result.PositionEvent) event3;
        assertEquals(397, positionEvent3.interval.start);
        assertEquals(432, positionEvent3.interval.end);

        //not
        SelectExpr s1not = SELECT(NOT(POSITION(b, 1,4))).FROM("SNGS-060");
        List<Result> results1not = s1not.search();
        assertEquals(3, results1not.size());
        Result r1not = results1not.get(0);
        assertEquals("SNGS-060", r1not.instance);
        List<Result.ResultEvent> events1not = r1not.events;
        assertEquals(1, events1not.size());
        Result.ResultEvent event1not = events1not.getFirst();
        assertEquals("POSITION", event1not.type);
        Result.PositionEvent positionEvent1not = (Result.PositionEvent) event1not;
        assertEquals(157, positionEvent1not.interval.start);
        assertEquals(276, positionEvent1not.interval.end);

        Result r2not = results1not.get(1);
        List<Result.ResultEvent> events2not = r2not.events;
        Result.ResultEvent event2not = events2not.getFirst();
        Result.PositionEvent positionEvent2not = (Result.PositionEvent) event2not;
        assertEquals(307, positionEvent2not.interval.start);
        assertEquals(396, positionEvent2not.interval.end);

        Result r3not = results1not.get(2);
        List<Result.ResultEvent> events3not = r3not.events;
        Result.ResultEvent event3not = events3not.getFirst();
        Result.PositionEvent positionEvent3not = (Result.PositionEvent) event3not;
        assertEquals(433, positionEvent3not.interval.start);
        assertEquals(750, positionEvent3not.interval.end);

        //PLAYER POSITION

        //wc
        Player pwc4 = PLAYER("pwc4");
        SelectExpr spl1 = SELECT(POSITION(pwc4,12)).FROM("SNGS-060").WHERE(STARTMIN(150), ENDMAX(500), MAXDURATION(200), MINDURATION(50), RECTANGLE(-50,0,30,30));
        List<Result> resultspl1 = spl1.search();
        assertEquals(2, resultspl1.size());

        Result rpl1 = resultspl1.get(0);
        assertEquals("SNGS-060", rpl1.instance);
        List<Result.ResultEvent> eventspl1 = rpl1.events;
        assertEquals(1, eventspl1.size());
        Result.ResultEvent eventpl1 = eventspl1.getFirst();
        assertEquals("POSITION", eventpl1.type);
        Result.PositionEvent positionEventpl1 = (Result.PositionEvent) eventpl1;
        assertEquals(List.of(3), positionEventpl1.playerIds);
        assertEquals(198, positionEventpl1.interval.start);
        assertEquals(279, positionEventpl1.interval.end);

        Result rpl2 = resultspl1.get(1);
        assertEquals("SNGS-060", rpl2.instance);
        List<Result.ResultEvent> eventspl2 = rpl2.events;
        assertEquals(1, eventspl2.size());
        Result.ResultEvent eventpl2 = eventspl2.getFirst();
        assertEquals("POSITION", eventpl1.type);
        Result.PositionEvent positionEventpl2 = (Result.PositionEvent) eventpl2;
        assertEquals(List.of(22), positionEventpl2.playerIds);
        assertEquals(171, positionEventpl2.interval.start);
        assertEquals(295, positionEventpl2.interval.end);

        //team
        Player pleft4 = PLAYER("pleft4", "left");
        SelectExpr s2 = SELECT(POSITION(pleft4,12)).FROM("SNGS-060").WHERE(RADIUS(-50,0,30));
        List<Result> results2 = s2.search();
        assertEquals(1, results2.size());

        Result r2pl = results2.get(0);
        assertEquals("SNGS-060", r2pl.instance);
        List<Result.ResultEvent> events2pl = r2pl.events;
        assertEquals(1, events2pl.size());
        Result.ResultEvent event2pl = events2pl.getFirst();
        assertEquals("POSITION", event2pl.type);
        Result.PositionEvent positionionEvent2pl = (Result.PositionEvent) event2pl;
        assertEquals(List.of(22), positionionEvent2pl.playerIds);
        assertEquals(171, positionionEvent2pl.interval.start);
        assertEquals(295, positionionEvent2pl.interval.end);

        //id
        Player p22 = PLAYER("p22", 22);
        SelectExpr s3 = SELECT(NOT(POSITION(p22,12))).FROM("SNGS-060");
        List<Result> results3 = s3.search();
        assertEquals(2, results3.size());

        Result not0 = results3.get(0);
        Result.PositionEvent notevent0 = (Result.PositionEvent) not0.events.getFirst();
        assertEquals(List.of(22), notevent0.playerIds);
        assertEquals(0, notevent0.interval.start);
        assertEquals(170, notevent0.interval.end);

        Result not1 = results3.get(1);
        Result.PositionEvent notevent1 = (Result.PositionEvent) not1.events.getFirst();
        assertEquals(List.of(22), notevent1.playerIds);
        assertEquals(296, notevent1.interval.start);
        assertEquals(750, notevent1.interval.end);


    }

    @Test
    public void TestPossession() throws IOException {

        //PLAYER POSSESSION

        //wc
        Player pwc4 = PLAYER("pwc4");
        SelectExpr s1 = SELECT(POSSESSION(pwc4)).FROM("SNGS-061").WHERE(RECTANGLE(-30, 5, 20, 20), STARTMIN(100), ENDMAX(200), MAXDURATION(50),MINDURATION(20));
        List<Result> results1 = s1.search();
        assertEquals(1, results1.size());
        Result r1 = results1.get(0);
        assertEquals("SNGS-061", r1.instance);
        List<Result.ResultEvent> events1 = r1.events;
        assertEquals(1, events1.size());
        Result.ResultEvent event1 = events1.getFirst();
        assertEquals("POSSESSION", event1.type);
        Result.PossessionEvent possessionEvent1 = (Result.PossessionEvent) event1;
        assertEquals(List.of(14), possessionEvent1.playerIds);
        assertEquals(116, possessionEvent1.interval.start);
        assertEquals(139, possessionEvent1.interval.end);

        //team
        Player pleft4 = PLAYER("pleft4", "left");
        SelectExpr s2 = SELECT(POSSESSION(pleft4)).FROM("SNGS-061").WHERE(RADIUS(-20,-20,40));
        List<Result> results2 = s2.search();
        assertEquals(2, results2.size());

        Result r2 = results2.get(0);
        assertEquals("SNGS-061", r2.instance);
        List<Result.ResultEvent> events2 = r2.events;
        assertEquals(1, events2.size());
        Result.ResultEvent event2 = events2.getFirst();
        assertEquals("POSSESSION", event2.type);
        Result.PossessionEvent possessionEvent2 = (Result.PossessionEvent) event2;
        assertEquals(List.of(11), possessionEvent2.playerIds);
        assertEquals(50, possessionEvent2.interval.start);
        assertEquals(84, possessionEvent2.interval.end);

        Result r3 = results2.get(1);
        Result.PossessionEvent events3 = (Result.PossessionEvent) r3.events.getFirst();
        assertEquals(List.of(14), events3.playerIds);
        assertEquals(116, events3.interval.start);
        assertEquals(139, events3.interval.end);

        //id
        Player p14 = PLAYER("p14", 14);
        SelectExpr s3 = SELECT(NOT(POSSESSION(p14))).FROM("SNGS-061");
        List<Result> results3 = s3.search();
        assertEquals(3, results3.size());

        Result not0 = results3.get(0);
        Result.PossessionEvent notevent0 = (Result.PossessionEvent) not0.events.getFirst();
        assertEquals(List.of(14), notevent0.playerIds);
        assertEquals(0, notevent0.interval.start);
        assertEquals(115, notevent0.interval.end);

        Result not1 = results3.get(1);
        Result.PossessionEvent notevent1 = (Result.PossessionEvent) not1.events.getFirst();
        assertEquals(List.of(14), notevent1.playerIds);
        assertEquals(140, notevent1.interval.start);
        assertEquals(356, notevent1.interval.end);

        Result not2 = results3.get(2);
        Result.PossessionEvent notevent2 = (Result.PossessionEvent) not2.events.getFirst();
        assertEquals(List.of(14), notevent2.playerIds);
        assertEquals(141, notevent2.interval.start);
        assertEquals(750, notevent2.interval.end);

        Result not3 = results3.get(3);
        Result.PossessionEvent notevent3 = (Result.PossessionEvent) not3.events.getFirst();
        assertEquals(List.of(14), notevent3.playerIds);
        assertEquals(371, notevent3.interval.start);
        assertEquals(750, notevent3.interval.end);


        //TEAM POSSESSION

    }


    @Test
    public void TestAND() throws IOException {

    }

    @Test
    public void TestOR() throws IOException {

    }

    // total/event max duration, min duration, start, end,
    // at least, at most

}
