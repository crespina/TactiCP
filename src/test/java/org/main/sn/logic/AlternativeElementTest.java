package org.main.sn.logic;

import org.junit.jupiter.api.Test;
import org.maxicp.cp.engine.constraints.IsEqual;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.maxicp.cp.CPFactory.*;

/**
 * {@link AlternativeElement} on small random instances: the solutions found by search are exactly
 * those of its definition, enumerated by brute force, and with a mandatory main interval its
 * pruning at the root is that of MaxiCP's {@code Alternative} with a reified channel
 * {@code present(alt_c) <=> chosen = c}.
 */
public class AlternativeElementTest {

    private static final int HORIZON = 6;

    /** The bounds and presence of one interval, as drawn: [startMin, startMax], ... */
    record Spec(int presence, int startMin, int startMax, int endMin, int endMax, int lengthMin, int lengthMax) {
        // presence: 0 absent, 1 present, 2 undecided
    }

    record Instance(Spec main, Spec[] alts, int[] chosenDomain, int absentValue) {}

    private static Spec randomSpec(Random r, boolean mayBeAbsent) {
        int startMin = r.nextInt(HORIZON - 1);
        int startMax = startMin + r.nextInt(HORIZON - 1 - startMin);
        int lengthMin = 1 + r.nextInt(2);
        int lengthMax = lengthMin + r.nextInt(3);
        int endMin = startMin + lengthMin + (r.nextInt(3) == 0 ? 1 : 0);
        int endMax = Math.min(HORIZON, startMax + lengthMax - (r.nextInt(3) == 0 ? 1 : 0));
        int draw = r.nextInt(8);
        int presence = !mayBeAbsent ? 1 : draw == 0 ? 0 : draw == 1 ? 1 : 2;
        return new Spec(presence, startMin, startMax, endMin, endMax, lengthMin, lengthMax);
    }

    private static Instance randomInstance(Random r) {
        int k = 1 + r.nextInt(3);
        boolean optionalMain = r.nextBoolean();
        Spec main = randomSpec(r, optionalMain);
        Spec[] alts = new Spec[k];
        for (int c = 0; c < k; c++) alts[c] = randomSpec(r, true);
        List<Integer> dom = new ArrayList<>();
        for (int c = 0; c < k; c++) if (r.nextInt(5) != 0) dom.add(c);
        if (dom.isEmpty()) dom.add(r.nextInt(k));
        int absentValue = optionalMain ? dom.get(r.nextInt(dom.size())) : -1;
        return new Instance(main, alts, dom.stream().mapToInt(Integer::intValue).toArray(), absentValue);
    }

    /** Creates the interval of {@code s}, or throws if its bounds are empty and it is present. */
    private static CPIntervalVar interval(CPSolver cp, Spec s) {
        CPIntervalVar itv = s.presence() == 1 ? makeIntervalVar(cp, false, 0, HORIZON) : makeIntervalVar(cp, 0, HORIZON);
        assertTrue(s.presence() == 1 ? itv.isPresent() : itv.isOptional());
        if (s.presence() == 0) itv.setAbsent();
        itv.setStartMin(s.startMin());
        itv.setStartMax(s.startMax());
        itv.setEndMin(s.endMin());
        itv.setEndMax(s.endMax());
        itv.setLengthMin(s.lengthMin());
        itv.setLengthMax(s.lengthMax());
        return itv;
    }

    /** The values an interval may take as drawn: "-" for absent, "s,e" otherwise. */
    private static List<String> valuesOf(Spec s) {
        List<String> values = new ArrayList<>();
        if (s.presence() != 1) values.add("-");
        if (s.presence() != 0) {
            for (int st = s.startMin(); st <= s.startMax(); st++) {
                for (int e = Math.max(s.endMin(), st + s.lengthMin()); e <= Math.min(s.endMax(), st + s.lengthMax()); e++) {
                    values.add(st + "," + e);
                }
            }
        }
        return values;
    }

    /** Every assignment of the definition, by brute force. */
    private static Set<String> bruteForce(Instance in) {
        Set<String> solutions = new HashSet<>();
        int k = in.alts().length;
        List<List<String>> altValues = new ArrayList<>();
        for (Spec s : in.alts()) altValues.add(valuesOf(s));
        for (int chosen : in.chosenDomain()) {
            for (String main : valuesOf(in.main())) {
                // the alternatives are fixed by main and chosen: either absent or equal to main
                String[] alts = new String[k];
                boolean ok = true;
                for (int c = 0; c < k; c++) {
                    alts[c] = !main.equals("-") && c == chosen ? main : "-";
                    ok &= altValues.get(c).contains(alts[c]);
                }
                if (main.equals("-")) ok &= chosen == in.absentValue();
                if (ok) solutions.add(key(chosen, main, alts));
            }
        }
        return solutions;
    }

    private static String key(int chosen, String main, String[] alts) {
        return chosen + " " + main + " " + String.join(" ", alts);
    }

    private static String valueOf(CPIntervalVar itv) {
        return itv.isAbsent() ? "-" : itv.startMin() + "," + itv.endMin();
    }

    /** The choice variable, with the drawn domain. */
    private static CPIntVar chosen(CPSolver cp, Instance in) {
        CPIntVar chosen = makeIntVar(cp, in.alts().length);
        for (int c = 0; c < in.alts().length; c++) {
            int value = c;
            if (Arrays.stream(in.chosenDomain()).noneMatch(v -> v == value)) chosen.remove(c);
        }
        return chosen;
    }

    /** Every solution found by search on the constraint. */
    private static Set<String> search(Instance in) {
        CPSolver cp = makeSolver();
        Set<String> solutions = new HashSet<>();
        CPIntervalVar main;
        CPIntervalVar[] alts = new CPIntervalVar[in.alts().length];
        CPIntVar chosen = chosen(cp, in);
        try {
            main = interval(cp, in.main());
            for (int c = 0; c < alts.length; c++) alts[c] = interval(cp, in.alts()[c]);
            cp.post(new AlternativeElement(main, alts, chosen, in.absentValue()));
        } catch (InconsistencyException e) {
            return solutions;
        }
        List<CPIntervalVar> all = new ArrayList<>(List.of(alts));
        all.addFirst(main);
        CPIntVar[] vars = Stream.concat(Stream.of(chosen), all.stream().flatMap(itv ->
                Stream.of(itv.status(), startOr(itv, -1), endOr(itv, -1)))).toArray(CPIntVar[]::new);
        DFSearch dfs = makeDfs(cp, Searches.firstFailBinary(vars));
        final CPIntervalVar m = main;
        dfs.onSolution(() -> {
            String[] a = new String[alts.length];
            for (int c = 0; c < alts.length; c++) a[c] = valueOf(alts[c]);
            assertTrue(solutions.add(key(chosen.min(), valueOf(m), a)), "duplicate solution");
        });
        dfs.solve();
        return solutions;
    }

    @Test
    public void searchFindsExactlyTheSolutionsOfTheDefinition() {
        Random r = new Random(20261104);
        int nonEmpty = 0, optionalMain = 0;
        for (int t = 0; t < 20000; t++) {
            Instance in = randomInstance(r);
            Set<String> expected = bruteForce(in);
            Set<String> found = search(in);
            assertEquals(expected, found, "instance " + t + ": " + in);
            if (!expected.isEmpty()) nonEmpty++;
            if (in.absentValue() >= 0 && expected.stream().anyMatch(s -> s.split(" ")[1].equals("-"))) optionalMain++;
        }
        // the random instances must exercise both outcomes
        assertTrue(nonEmpty > 5000, "only " + nonEmpty + " instances with solutions");
        assertTrue(optionalMain > 1000, "only " + optionalMain + " instances with an absent main");
    }

    /** Bounds and presence of the interval after the root propagation, or null after a failure. */
    private static String rootState(Instance in, boolean alternativeElement) {
        CPSolver cp = makeSolver();
        CPIntVar chosen = chosen(cp, in);
        try {
            CPIntervalVar main = interval(cp, in.main());
            CPIntervalVar[] alts = new CPIntervalVar[in.alts().length];
            for (int c = 0; c < alts.length; c++) alts[c] = interval(cp, in.alts()[c]);
            if (alternativeElement) {
                cp.post(new AlternativeElement(main, alts, chosen, -1));
            } else {
                chosen.removeAbove(alts.length - 1);
                cp.post(alternative(main, alts));
                for (int c = 0; c < alts.length; c++) cp.post(new IsEqual(alts[c].status(), chosen, c));
            }
            StringBuilder sb = new StringBuilder(chosen.toString()).append(' ').append(state(main));
            for (CPIntervalVar alt : alts) sb.append(' ').append(state(alt));
            return sb.toString();
        } catch (InconsistencyException e) {
            return null;
        }
    }

    private static String state(CPIntervalVar itv) {
        if (itv.isAbsent()) return "absent";
        return (itv.isPresent() ? "present" : "optional") + "[" + itv.startMin() + ".." + itv.startMax() + ", "
                + itv.endMin() + ".." + itv.endMax() + ", " + itv.lengthMin() + ".." + itv.lengthMax() + "]";
    }

    @Test
    public void prunesAsAlternativeWithAReifiedChannelWhenMainIsMandatory() {
        Random r = new Random(4);
        int compared = 0;
        for (int t = 0; t < 20000; t++) {
            Instance drawn = randomInstance(r);
            Instance in = new Instance(new Spec(1, drawn.main().startMin(), drawn.main().startMax(), drawn.main().endMin(),
                    drawn.main().endMax(), drawn.main().lengthMin(), drawn.main().lengthMax()),
                    drawn.alts(), drawn.chosenDomain(), -1);
            String element = rootState(in, true);
            String channel = rootState(in, false);
            assertEquals(channel, element, "instance " + t + ": " + in);
            if (element != null) compared++;
        }
        assertTrue(compared > 5000, "only " + compared + " consistent instances");
    }
}
