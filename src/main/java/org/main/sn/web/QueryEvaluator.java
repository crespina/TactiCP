package org.main.sn.web;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.main.sn.dsl.*;

import java.io.IOException;

/**
 * Evaluates a query string written in the DSL (Java/Groovy syntax) and returns
 * the resulting SelectExpr.  The user writes exactly what they would write in
 * the static context of {@link Factory}, e.g.:
 * <pre>
 *   SELECT(PLAYER("p1").PASSTO(PLAYER("p2")).MINRANGE()).FROM("SNGS-060").SEARCH(0)
 * </pre>
 * All Factory static methods are imported into the Groovy binding so the user
 * doesn't have to prefix them.
 */
public class QueryEvaluator {

    public static SelectExpr evaluate(String dslQuery) throws IOException {
        // Import all static factory methods into scope
        String script = """
                import static org.main.sn.dsl.Factory.*
                import org.main.sn.dsl.*
                """ + dslQuery;

        Binding binding = new Binding();
        GroovyShell shell = new GroovyShell(QueryEvaluator.class.getClassLoader(), binding);
        Object result = shell.evaluate(script);
        if (result instanceof SelectExpr se) {
            return se;
        }
        throw new IllegalArgumentException("Query did not evaluate to a SelectExpr. Got: "
                + (result == null ? "null" : result.getClass().getName()));
    }
}

