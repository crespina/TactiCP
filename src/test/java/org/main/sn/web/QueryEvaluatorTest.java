package org.main.sn.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.main.sn.dsl.SelectExpr;
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

    /** The syntax check builds the query as written, but loads no game and runs no search. */
    @Test
    void checkingLoadsNoGameAndRunsNoSearch() throws Exception {
        SelectExpr se = QueryEvaluator.parseStructure(
                "SELECT(POSITION(BALL(), 1, 2)).FROM(\"all\").COUNT()");
        assertTrue(se.matches.isEmpty(), "no game is loaded");
        assertTrue(se.isCount(), "COUNT() is recorded, not run");
        assertEquals("all", se.dataset());
    }

    /** Rules that do not depend on the data are checked while the query is built. */
    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT(NOT(PLAYER(\"a\").PASSTO(PLAYER(\"b\"))))",
            "SELECT(NOT(BALL().MOVETO(1, 2)))",
            "SELECT(NOT(PLAYER(\"a\").MOVETO(1, 2)))",
            "SELECT(NOT(TEAM(\"left\").MOVETO(1, 2, 3)))",
            "SELECT(NOT(AND(POSITION(BALL(), 1), PLAYER(\"a\").PASSTO(PLAYER(\"b\")))))",
            "SELECT(BALL().MOVETO(3, 3))",
            "SELECT(PLAYER(\"a\").MOVETO(3, 3))",
            "SELECT(TEAM(\"left\").MOVETO(3, 3, 2))",
            "SELECT(TEAM(\"left\").MOVETO(1, 2, 0))",
            "SELECT(POSITION(TEAM(\"left\"), K(0), 1, 2))",
            "SELECT(OR(POSITION(BALL(), 1)))",
            "SELECT(AND(POSITION(BALL(), 1)))",
    })
    void refusesWhatTheCompilerCannotModel(String select) {
        assertThrows(IllegalArgumentException.class,
                () -> QueryEvaluator.checkSyntax(select + ".FROM(\"SNGS-060\")"));
    }

    @Test
    void acceptsADoubleNegation() throws Exception {
        assertNotNull(QueryEvaluator.parseStructure(
                "SELECT(NOT(NOT(PLAYER(\"a\").PASSTO(PLAYER(\"b\"))))).FROM(\"SNGS-060\")"));
    }

    @Test
    void refusesACallAfterCount() {
        assertThrows(IllegalArgumentException.class, () -> QueryEvaluator.checkSyntax(
                "SELECT(POSITION(BALL(), 1)).FROM(\"SNGS-060\").COUNT().SEARCH(1)"));
    }

    @Test
    void refusesAMissingFromAndAnUnknownGame() {
        assertThrows(IllegalArgumentException.class,
                () -> QueryEvaluator.checkSyntax("SELECT(POSITION(BALL(), 1))"));
        assertThrows(IllegalArgumentException.class,
                () -> QueryEvaluator.checkSyntax("SELECT(POSITION(BALL(), 1)).FROM(\"SNGS-999\")"));
    }

    /** Running a COUNT query still answers a number. */
    @Test
    void evaluatesCountToANumber() throws Exception {
        Object n = QueryEvaluator.evaluate("SELECT(BALL().MOVETO(1, 2)).FROM(\"SNGS-060\").COUNT()");
        assertInstanceOf(Integer.class, n);
    }
}
