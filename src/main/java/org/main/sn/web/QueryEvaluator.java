package org.main.sn.web;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.main.sn.dsl.*;

import java.io.IOException;

/**
 * Evaluates a query string written in the DSL (Java/Groovy syntax) and returns
 * the resulting SelectExpr.  The user writes exactly what they would write in
 * the static context of {@link Factory}, e.g.:
 *   SELECT(PLAYER("p1").PASSTO(PLAYER("p2")).MINRANGE()).FROM("SNGS-060").SEARCH(0)
 * All Factory static methods are imported into the Groovy binding so the user
 * doesn't have to prefix them.
 */
public class QueryEvaluator {

    /**
     * Sentinel dataset used for syntax-only validation. It matches no split and no
     * game id, so {@link SelectExpr#FROM} builds an empty match set without parsing
     * any game-state JSON.
     */
    private static final String SYNTAX_CHECK_DATASET = "__syntaxcheck__";

    /**
     * Validates the <em>syntax</em> of a DSL query without executing it.
     * <p>
     * The query is rewritten before evaluation so that neither of the two expensive
     * operations runs:
     * <ul>
     *   <li>the dataset inside {@code .FROM("…")} is swapped for a sentinel that
     *       matches no split or game, so no game-state JSON is loaded;</li>
     *   <li>a trailing {@code .COUNT()} is dropped, since {@code COUNT()} would
     *       otherwise run the full constraint search.</li>
     * </ul>
     * Everything else — the {@code SELECT} structure, entities, events,
     * {@code WHERE} constraints and {@code SEARCH} mode — is still constructed, so
     * genuine syntax errors are caught. This is intentionally cheap: it never loads
     * data and never runs the solver, which matters most for {@code FROM("all")}.
     *
     * @param dslQuery the query as produced by the LLM (unchanged on return)
     * @throws IllegalArgumentException if the query has no FROM clause or does not
     *                                  build into a valid {@link SelectExpr}
     * @throws IOException              propagated from {@link #evaluate} (not expected,
     *                                  as the sentinel dataset loads nothing)
     */
    public static void checkSyntax(String dslQuery) throws IOException {
        parseStructure(dslQuery);   // builds the SelectExpr; throws on malformed DSL — no data, no search
    }

    /**
     * Builds the {@link SelectExpr} for a DSL query <em>without</em> loading any
     * game data or running the solver, and returns it for structural inspection.
     * <p>
     * The query is rewritten first so neither expensive operation runs: the
     * {@code .FROM("…")} dataset is swapped for a sentinel that matches no split or
     * game (so no game-state JSON is read), and a trailing {@code .COUNT()} is
     * dropped (so the result is always a {@link SelectExpr}, never an
     * {@link Integer}). Everything else — SELECT structure, entities, events,
     * WHERE constraints and SEARCH mode — is still constructed, so this is what an
     * AST-level comparison should parse.
     *
     * @throws IllegalArgumentException if the query has no FROM clause or does not
     *                                  build into a valid {@link SelectExpr}
     */
    public static SelectExpr parseStructure(String dslQuery) throws IOException {
        if (dslQuery == null || !dslQuery.contains(".FROM(")) {
            throw new IllegalArgumentException("Query must contain a .FROM(\"…\") clause.");
        }
        String probe = dslQuery
                .replaceAll("\\.FROM\\s*\\(\\s*\"[^\"]*\"\\s*\\)", ".FROM(\"" + SYNTAX_CHECK_DATASET + "\")")
                .replaceAll("\\.COUNT\\s*\\(\\s*\\)\\s*$", "");
        Object result = evaluate(probe);
        if (result instanceof SelectExpr se) return se;
        throw new IllegalArgumentException("Query did not build into a SelectExpr. Got: "
                + (result == null ? "null" : result.getClass().getName()));
    }

    /**
     * Evaluates the DSL query and returns either a {@link SelectExpr}
     * (for normal search queries) or an {@link Integer} (for COUNT queries).
     */
    public static Object evaluate(String dslQuery) throws IOException {
        // Import all static factory methods into scope
        String script = """
                import static org.main.sn.dsl.Factory.*
                import org.main.sn.dsl.*
                """ + dslQuery;

        Binding binding = new Binding();
        GroovyShell shell = new GroovyShell(QueryEvaluator.class.getClassLoader(), binding);
        Object result = shell.evaluate(script);
        if (result instanceof SelectExpr || result instanceof Integer) {
            return result;
        }
        throw new IllegalArgumentException("Query did not evaluate to a SelectExpr or COUNT. Got: "
                + (result == null ? "null" : result.getClass().getName()));
    }
}

