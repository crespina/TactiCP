package org.main.sn.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/** A query is run by Groovy: only DSL tokens may reach it. */
class QueryEvaluatorTest {

    /** Each would set the property {@code pwned} if Groovy ran it. */
    @ParameterizedTest
    @ValueSource(strings = {
            "System.setProperty(\"pwned\", \"1\")",
            "SELECT(BALL().MOVETO(1, 2)).FROM(\"${System.setProperty('pwned', '1')}\")",
            "SELECT(BALL().MOVETO(1, 2)).FROM(\"SNGS-060\"); System.setProperty(\"pwned\", \"1\")",
            "SELECT(BALL().MOVETO(1, 2)).FROM(\"SNGS-060\").with { System.setProperty('pwned', '1') }",
            "SELECT(BALL().MOVETO(1, 2)).FROM(\"SNGS-060\").class.forName(\"java.lang.System\")",
            "SELECT(BALL().MOVETO(1, 2)).FROM('SNGS-060')",
            "SELECT(BALL().MOVETO(1, 2)).FROM(\"\\u0024{1}\")",
            "SELECT(BALL().MOVETO(1, 2)).FROM(\"SNGS-060\") // comment",
            "EXECUTE(\"x\")",
    })
    void refusesAnythingButTheDsl(String query) {
        System.clearProperty("pwned");
        assertThrows(IllegalArgumentException.class, () -> QueryEvaluator.evaluate(query));
        assertNull(System.getProperty("pwned"));
    }

    @Test
    void refusesAPathInFrom() {
        assertThrows(IllegalArgumentException.class,
                () -> QueryEvaluator.evaluate("SELECT(BALL().MOVETO(1, 2)).FROM(\"SNGS-../../x\")"));
    }

    @Test
    void acceptsEveryQueryOfTheCorpus() throws Exception {
        JsonNode pairs = new ObjectMapper().readTree(new File("data/eval/query_pairs.json"));
        assertTrue(pairs.size() > 0);
        for (JsonNode pair : pairs) {
            String gold = pair.get("dsl").asString();
            String from = ".FROM(\"SNGS-060\")";
            String dsl = gold.endsWith(".COUNT()")       // COUNT() stays last
                    ? gold.substring(0, gold.length() - ".COUNT()".length()) + from + ".COUNT()"
                    : gold + from;
            assertDoesNotThrow(() -> QueryEvaluator.checkTokens(dsl), dsl);
            assertNotNull(QueryEvaluator.parseStructure(dsl), dsl);
        }
    }

    @Test
    void acceptsNegativeNumbersCountAndSearch() throws Exception {
        assertNotNull(QueryEvaluator.parseStructure(
                "SELECT(PLAYER(\"p1\", 7, \"left\").PASSTO(PLAYER(\"p2\")).MINRANGE())\n"
                        + "  .WHERE(RECTANGLE(-60, 20, 65, 40), MAXDURATION(25)).FROM(\"SNGS-060, SNGS-061\").SEARCH(1)"));
        assertNotNull(QueryEvaluator.parseStructure(
                "SELECT(POSITION(TEAM(\"left\"), K(3), 1, 2)).FROM(\"SNGS-060\").COUNT()"));
    }
}
