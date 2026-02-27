/*
 * MaxiCP is under MIT License
 * Copyright (c)  2023 UCLouvain
 */

package org.main.sn;


import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Event;
import org.main.sn.dsl.Player;
import org.main.sn.dsl.SelectExpr;
import org.main.sn.logic.RegularInterval;
import org.main.sn.logic.Result;
import org.main.util.Automaton;
import org.maxicp.cp.CPSolverTest;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.CPFactory;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.main.sn.dsl.Factory.*;

public class QueryTest {

    @Test
    public void TestPassTo() throws IOException {

        Player p1 = PLAYER("p1");
        Player p2 = PLAYER("p2");

        //PASS BETWEEN TWO WILDCARDS
        Event e1 = p1.PASSTO(p2).MINRANGE();
        SelectExpr s1 = SELECT(e1).FROM("SNGS-060, SNGS-061"); //toutes les passes

        List<Result> results = s1.search();
        assertEquals(7, results.size());


        Result r1 = results.get(0);
        assertEquals("train/SNGS-061", r1.instance);
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

        Result.PassEvent event3 = (Result.PassEvent) results.get(2).events.getFirst();
        assertEquals(13, event3.passerId);
        assertEquals(14, event3.receiverId);
        assertEquals(340, event3.interval.start);
        assertEquals(359, event3.interval.end);

        Result.PassEvent event4 = (Result.PassEvent) results.get(3).events.getFirst();
        assertEquals(11, event4.passerId);
        assertEquals(16, event4.receiverId);
        assertEquals(21, event4.interval.start);
        assertEquals(44, event4.interval.end);

        Result.PassEvent event5 = (Result.PassEvent) results.get(4).events.getFirst();
        assertEquals(16, event5.passerId);
        assertEquals(19, event5.receiverId);
        assertEquals(117, event5.interval.start);
        assertEquals(136, event5.interval.end);

        Result.PassEvent event6 = (Result.PassEvent) results.get(5).events.getFirst();
        assertEquals(19, event6.passerId);
        assertEquals(22, event6.receiverId);
        assertEquals(142, event6.interval.start);
        assertEquals(182, event6.interval.end);

        Result.PassEvent event7 = (Result.PassEvent) results.get(6).events.getFirst();
        assertEquals(23, event7.passerId);
        assertEquals(8, event7.receiverId);
        assertEquals(589, event7.interval.start);
        assertEquals(620, event7.interval.end);


        //PASS BETWEEN TWO IDS
        Player p3 = PLAYER("p3", 11);
        Player p4 = PLAYER("p4", 14);
        SelectExpr s2 = SELECT(p3.PASSTO(p4).MINRANGE()).FROM("SNGS-060, SNGS-061");
        List<Result> results2 = s2.search();
        assertEquals(1, results2.size());

        Result.PassEvent event8 = (Result.PassEvent) results2.get(0).events.getFirst();
        assertEquals(11, event8.passerId);
        assertEquals(14, event8.receiverId);
        assertEquals(84, event8.interval.start);
        assertEquals(117, event8.interval.end);


        //NOT PASS BETWEEN TEAM
        Player p5 = PLAYER("p5", "left");
        Player p6 = PLAYER("p6", "left");
        SelectExpr s3 = SELECT(NOT(p5.PASSTO(p6).MINRANGE())).FROM("SNGS-060").SEARCH(1);
        List<Result> results3 = s3.search();
        assertEquals(1, results3.size());

        Result.PassEvent event9 = (Result.PassEvent) results3.get(0).events.getFirst();
        assertEquals(0, event9.interval.start);
        assertEquals(21, event9.interval.end);





    }


}
