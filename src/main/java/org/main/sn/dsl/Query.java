package org.main.sn.dsl;

// Query.java

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class Query {
    private final Set<String> declaredNames = new HashSet<>();
    private final List<String> fromMatches = new ArrayList<>();
    private Pattern wherePattern;
    private Object selectObject; // Pattern, EventExpr, Window etc.

    private Query() {
    }

    public static QueryBuilder builder() {
        return new QueryBuilder();
    }

    public static final class QueryBuilder {
        private final Query q = new Query();

        public QueryBuilder fromDataset() {
            q.fromMatches.clear();
            return this;
        }

        public QueryBuilder fromMatches(String... ids) {
            Collections.addAll(q.fromMatches, ids);
            return this;
        }

        public QueryBuilder where(Pattern p) {
            // register any bound var names from pattern (simple example)
            // in real code you'd traverse AST & collect Var names → enforce no shadowing
            q.wherePattern = p;
            return this;
        }

        public QueryBuilder select(Object sel) {
            q.selectObject = sel;
            return this;
        }

        public Query build() {
            // perform basic checks: from non-empty or dataset, pattern not null, no shadowing
            return q;
        }
    }

    @Override
    public String toString() {
        return "Query{FROM=" + (fromMatches.isEmpty() ? "DATASET" : fromMatches) + " WHERE=" + wherePattern + " SELECT=" + selectObject + "}";
    }
}

