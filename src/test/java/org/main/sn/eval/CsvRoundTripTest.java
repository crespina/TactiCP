package org.main.sn.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The evaluation pipeline's {@code --resume} reads back the CSV it wrote, so the
 * writer and the reader must agree exactly: a row that survives the round trip
 * with shifted or mangled fields would let a run reuse the wrong result.
 */
class CsvRoundTripTest {

    /** Writes rows the way {@code ResultWriter} does, so both halves are exercised. */
    private static String write(String[]... rows) {
        StringBuilder sb = new StringBuilder();
        for (String[] row : rows) {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) sb.append(',');
                String v = row[i] == null ? "" : row[i];
                boolean quote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
                sb.append(quote ? '"' + v.replace("\"", "\"\"") + '"' : v);
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    void roundTripsFieldsThatNeedQuoting() {
        String[] row = {
                "pass-and-not-possession",
                "Find moments where player 11 passes, while player 3 does not have possession.",
                "SELECT(AND(PLAYER(\"p11\",11).PASSTO(PLAYER(\"p2\")).MINRANGE(), NOT(POSSESSION(PLAYER(\"p3\",3)))))",
                "SELECT(PLAYER(\"p11\",11).PASSTO(PLAYER(\"p2\")).MINRANGE())",
                "OUTPUT_MISMATCH",
                "event #1: one side is AND, the other is a plain event | gold=3 matches generated=7 matches",
                "a detail\nspanning two lines",
                "",
        };
        List<String[]> parsed = EvaluationPipeline.parseCsv(write(row));
        assertEquals(1, parsed.size());
        assertArrayEquals(row, parsed.get(0));
    }

    @Test
    void roundTripsAHeaderAndSeveralRows() {
        String[] a = {"1", "nl a", "SELECT(A)", "PASS"};
        String[] b = {"2", "nl, with comma", "SELECT(\"B\")", "PASS"};
        List<String[]> parsed = EvaluationPipeline.parseCsv(write(a, b));
        assertEquals(2, parsed.size());
        assertArrayEquals(a, parsed.get(0));
        assertArrayEquals(b, parsed.get(1));
    }

    @Test
    void keepsEmptyTrailingAndInteriorFields() {
        String[] row = {"", "x", "", "", "y", ""};
        assertArrayEquals(row, EvaluationPipeline.parseCsv(write(row)).get(0));
    }

    @Test
    void ignoresATrailingNewlineAndAcceptsCrLf() {
        assertEquals(1, EvaluationPipeline.parseCsv("a,b\n").size());
        assertArrayEquals(new String[]{"a", "b"}, EvaluationPipeline.parseCsv("a,b\r\n").get(0));
        assertEquals(0, EvaluationPipeline.parseCsv("").size());
        assertEquals(0, EvaluationPipeline.parseCsv("\n").size());
    }

    /** A half-written final row (the run was killed mid-flush) must not crash the reader. */
    @Test
    void toleratesATruncatedLastRow() {
        List<String[]> parsed = EvaluationPipeline.parseCsv("a,b,c\nd,e");
        assertEquals(2, parsed.size());
        assertEquals(2, parsed.get(1).length);
    }
}
