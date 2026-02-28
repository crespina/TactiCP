package org.main.sn.dsl;

import java.util.Arrays;
import java.util.Objects;

/** A single where-constraint (END, WITHIN, START, etc.). */

public interface Where {
    Kind kind();
    int[] values();

    enum Kind { START, END, WITHIN, MIN_DURATION, RECTANGLE, RADIUS, ATMOST, ATLEAST, COUNT}

    static Where of(Kind kind, int... values) {
        return new SimpleWhere(kind, values);
    }

    final class SimpleWhere implements Where {
        private final Kind kind;
        private final int[] values;

        SimpleWhere(Kind kind, int... values) {
            this.kind = Objects.requireNonNull(kind, "kind");
            this.values = values;
        }

        @Override public Kind kind() { return kind; }
        @Override public int[] values() { return values; }

        @Override public String toString() { return kind + "(" + Arrays.toString(values) + ")"; }
    }
}
