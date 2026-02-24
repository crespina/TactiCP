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

        Event e1 = p1.PASSTO(p2).MINRANGE();
        SelectExpr s1 = SELECT(e1).FROM("SNGS-060, SNGS-061"); //toutes les passes
        List<String> toPrint = s1.searchAndReturn();
        for (String s : toPrint) {
            System.out.println(s);
        }
        assertEquals(1,1);

    }


}
