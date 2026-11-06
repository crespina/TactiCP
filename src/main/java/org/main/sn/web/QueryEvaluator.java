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
     * Checks that a DSL query is a well-formed query of the language, without running it.
     * <p>
     * The query is built exactly as written: no game is loaded and the solver never runs, since
     * building a query only records and checks what it is given (see {@link SelectExpr}). Every
     * rule that does not depend on the data is checked while it is built.
     *
     * @param dslQuery the query as produced by the LLM
     * @throws IllegalArgumentException if the query has no FROM clause, names a game that is not in
     *                                  the dataset, or is not a well-formed query
     * @throws IOException              if the folder of a split named in FROM cannot be listed
     */
    public static void checkSyntax(String dslQuery) throws IOException {
        parseStructure(dslQuery);
    }

    /**
     * Builds the {@link SelectExpr} of a DSL query that must have a FROM clause, without running
     * it (see {@link #checkSyntax}), and returns it for structural inspection.
     *
     * @throws IllegalArgumentException if the query has no FROM clause or is not well formed
     */
    public static SelectExpr parseStructure(String dslQuery) throws IOException {
        SelectExpr se = parse(dslQuery);
        if (se.dataset() == null) {
            throw new IllegalArgumentException("Query must contain a .FROM(\"…\") clause.");
        }
        return se;
    }

    /**
     * Builds the {@link SelectExpr} of a DSL query, with or without a FROM clause, without
     * running it: no game is loaded and the solver never runs.
     *
     * @throws IllegalArgumentException if the query is not well formed
     */
    public static SelectExpr parse(String dslQuery) throws IOException {
        checkTokens(dslQuery);
        // Import all static factory methods into scope
        String script = """
                import static org.main.sn.dsl.Factory.*
                import org.main.sn.dsl.*
                """ + dslQuery;

        Binding binding = new Binding();
        GroovyShell shell = new GroovyShell(QueryEvaluator.class.getClassLoader(), binding);
        Object result = shell.evaluate(script);
        if (result instanceof SelectExpr se) return se;
        throw new IllegalArgumentException("Query did not build into a SelectExpr. Got: "
                + (result == null ? "null" : result.getClass().getName()));
    }

    /**
     * Builds and runs a DSL query. Returns the number of matches, as an {@link Integer}, for a
     * query ending with COUNT(); otherwise the built {@link SelectExpr}, not yet searched: the
     * caller runs it with {@link SelectExpr#search()}.
     */
    public static Object evaluate(String dslQuery) throws IOException {
        SelectExpr se = parse(dslQuery);
        return se.isCount() ? (Object) se.count() : se;
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

