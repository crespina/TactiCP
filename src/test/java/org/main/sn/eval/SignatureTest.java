package org.main.sn.eval;

import org.junit.jupiter.api.Test;
import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;
import org.main.sn.logic.Result;
import org.main.sn.web.QueryEvaluator;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The evaluation compares two queries by the atomic events of their matches, step by step: the
 * way the events are grouped is not observable, whereas their intervals and players are.
 */
public class SignatureTest {

    private static List<String> signatures(String dsl) throws IOException {
        Factory.reset();
        List<Result> results = ((SelectExpr) QueryEvaluator.evaluate(dsl)).search();
        Factory.reset();
        return results.stream().map(EvaluationPipeline::signature).sorted().toList();
    }

    @Test
    public void groupingThatReportsTheSameEventsIsTheSameMatch() throws IOException {
        // corpus pair nest-seq-group-where: gpt-oss distributed the AND over the OR, which reports
        // the same atomic events, and used to fail on SNGS-037
        String gold = "SELECT(OR(POSSESSION(PLAYER(\"p1\",2)),POSSESSION(PLAYER(\"p2\",3))),"
                + "AND(OR(POSITION(BALL(),6),POSITION(BALL(),7)),NOT(POSSESSION(TEAM(\"right\")))).WHERE(MINDURATION(10)))"
                + ".FROM(\"SNGS-037\").WHERE(MAXDURATION(250))";
        String distributed = "SELECT(OR(POSSESSION(PLAYER(\"p2\",2)),POSSESSION(PLAYER(\"p3\",3))),"
                + "OR(AND(POSITION(BALL(),6),NOT(POSSESSION(TEAM(\"right\")))),AND(POSITION(BALL(),7),NOT(POSSESSION(TEAM(\"right\"))))).WHERE(MINDURATION(10)))"
                + ".FROM(\"SNGS-037\").WHERE(MAXDURATION(250))";
        List<String> expected = signatures(gold);
        assertFalse(expected.isEmpty());
        assertEquals(expected, signatures(distributed));

        // the members of an AND in any order
        assertEquals(signatures("SELECT(AND(POSSESSION(PLAYER(\"p\",7)),POSITION(BALL(),1,2,3,4))).FROM(\"SNGS-061\")"),
                signatures("SELECT(AND(POSITION(BALL(),1,2,3,4),POSSESSION(PLAYER(\"p\",7)))).FROM(\"SNGS-061\")"));
    }

    @Test
    public void differentEventsAreStillDifferentMatches() throws IOException {
        // a set of zones gives one interval while the ball stays in either zone, an OR one per zone
        String set = "SELECT(POSITION(BALL(),1,4)).FROM(\"SNGS-061\")";
        String or = "SELECT(OR(POSITION(BALL(),1),POSITION(BALL(),4))).FROM(\"SNGS-061\")";
        assertNotEquals(signatures(set), signatures(or));
        // the order of the steps of a sequence matters
        assertNotEquals(signatures("SELECT(POSITION(BALL(),1),POSITION(BALL(),4)).FROM(\"SNGS-061\")"),
                signatures("SELECT(POSITION(BALL(),4),POSITION(BALL(),1)).FROM(\"SNGS-061\")"));
    }
}
