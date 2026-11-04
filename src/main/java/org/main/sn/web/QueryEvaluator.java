package org.main.sn.web;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.main.sn.dsl.*;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        checkTokens(dslQuery);
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

    /**
     * The words of the DSL: the upper-case public methods of its classes ({@code SELECT}, {@code PLAYER},
     * {@code PASSTO}, {@code WHERE}, {@code FROM}, ...).
     */
    private static final Set<String> WORDS = dslWords();

    /**
     * One token of a query: whitespace, a DSL word, a plain string literal (no {@code $}, which would
     * start a Groovy interpolation, and no escapes), an integer or decimal number, or {@code ( ) , .}.
     */
    private static final Pattern TOKEN = Pattern.compile(
            "\\s+|[A-Z][A-Z_]*|\"[^\"\\\\$\\r\\n]*\"|-?\\d+(?:\\.\\d+)?|[(),.]");

    private static Set<String> dslWords() {
        Set<String> words = new TreeSet<>();
        for (Class<?> c : List.of(Factory.class, SelectExpr.class, Event.class, Player.class, Team.class, Ball.class)) {
            for (Method m : c.getMethods()) {
                if (m.getName().matches("[A-Z][A-Z_]*")) words.add(m.getName());
            }
        }
        return words;
    }

    /**
     * Refuses a query that contains anything but DSL tokens. The query is run by Groovy, which would
     * otherwise execute any code it is given (a method call such as {@code "cmd".execute()}, an
     * interpolated string, a closure, an import): restricted to these tokens, it can only call the DSL.
     *
     * @throws IllegalArgumentException naming the first character or word that is not allowed
     */
    static void checkTokens(String dslQuery) {
        if (dslQuery == null) throw new IllegalArgumentException("Empty query.");
        Matcher m = TOKEN.matcher(dslQuery);
        int at = 0;
        while (at < dslQuery.length()) {
            m.region(at, dslQuery.length());
            if (!m.lookingAt()) {
                throw new IllegalArgumentException("Unexpected character '" + dslQuery.charAt(at)
                        + "' at position " + at + " of the query.");
            }
            String token = m.group();
            if (Character.isUpperCase(token.charAt(0)) && !WORDS.contains(token)) {
                throw new IllegalArgumentException("Unknown word '" + token + "' at position " + at + " of the query.");
            }
            at = m.end();
        }
    }
}

