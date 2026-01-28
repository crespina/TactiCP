package org.main.sn.dsl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Optional: groups multiple Where constraints in one object. */
public final class WhereClause {
    private final List<Where> parts = new ArrayList<>();

    public WhereClause and(Where w) {
        parts.add(w);
        return this;
    }

    public List<Where> parts() {
        return parts;
    }

    public static WhereClause of(Where... ws) {
        WhereClause c = new WhereClause();
        if (ws != null) for (Where w : ws) if (w != null) c.and(w);
        return c;
    }
}