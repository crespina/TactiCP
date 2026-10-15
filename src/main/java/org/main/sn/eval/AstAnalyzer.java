package org.main.sn.eval;

import org.main.sn.dsl.*;
import org.main.sn.web.QueryEvaluator;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Structural (AST-level) comparison of two DSL queries — the reference ("gold")
 * translation and the one produced by the LLM.
 *
 * <p>The evaluation pipeline decides pass/fail purely from the <em>output</em> of
 * running both queries. This class is only a diagnostic: when a pair fails, it
 * localises <em>where</em> the generated query first diverges from the gold one
 * (Entity? Event? Combinator? WHERE? …), so failures can be bucketed and studied.
 *
 * <p>Both queries are parsed into a normalised AST via
 * {@link QueryEvaluator#parseStructure(String)} (no data is loaded, no solver
 * runs). Player <em>names</em> are replaced by coreference indices assigned in
 * order of first appearance, so two queries that use different labels for the
 * same structure (e.g. {@code PLAYER("p1")} vs {@code PLAYER("a")}) are treated as
 * equal, while the "same name used twice = same player" rule is still compared
 * (gold {@code (p,p)} vs generated {@code (p1,p2)} shows up as a coreference diff).
 */
public final class AstAnalyzer {

    private AstAnalyzer() {}

    /** Coarse bucket for where a failure lives. */
    public enum Category {
        EQUAL,          // ASTs are structurally identical
        STRUCTURE,      // different number of events / group children (sequence shape)
        COMBINATOR,     // AND vs OR vs plain sequence
        EVENT,          // event type, negation, or its zone/K parameters
        MINRANGE,       // .MINRANGE() present on one side only
        ENTITY,         // player jersey/team/coreference, ball vs player, team membership
        WHERE,          // WHERE constraints (temporal/spatial/aggregation) differ
        SEARCHMODE,     // .COUNT()/.SEARCH(...) differ
        PARSE           // one side could not be parsed into an AST
    }

    /** Result of a structural comparison. */
    public record Diff(Category category, String detail) {
        public boolean isEqual() { return category == Category.EQUAL; }
    }

    private static final Pattern COUNT = Pattern.compile("\\.COUNT\\s*\\(\\s*\\)");

    /**
     * Compares two DSL strings structurally and returns the first divergence.
     * Never throws: a parse problem is reported as {@link Category#PARSE}.
     */
    public static Diff analyze(String goldDsl, String genDsl) {
        NQuery gold;
        try {
            gold = build(goldDsl);
        } catch (Exception e) {
            return new Diff(Category.PARSE, "gold query could not be parsed: " + e.getMessage());
        }
        NQuery gen;
        try {
            gen = build(genDsl);
        } catch (Exception e) {
            return new Diff(Category.PARSE, "generated query could not be parsed: " + e.getMessage());
        }
        return diff(gold, gen);
    }

    // ------------------------------------------------------------------ //
    // Normalised AST
    // ------------------------------------------------------------------ //

    private interface NNode {}

    private record NGroup(String kind, boolean negated, List<NNode> children) implements NNode {}

    private record NLeaf(String type, boolean negated, boolean minrange,
                         List<NEntity> entities, List<Integer> params,
                         Map<String, Integer> where) implements NNode {}

    private record NEntity(String kind, int coref, Integer id, String team,
                           String teamName, List<Integer> teamIds) {}

    private record NQuery(List<NNode> steps, String searchMode,
                          Map<String, Integer> selectWhere) {}

    /** Builds the normalised AST for one query, assigning coreference indices. */
    private static NQuery build(String dsl) throws java.io.IOException {
        Factory.reset();
        SelectExpr se = QueryEvaluator.parseStructure(dsl);

        // Pass 1: assign a coreference index to every distinct player name,
        // in order of first appearance across the ordered event walk.
        Map<String, Integer> coref = new LinkedHashMap<>();
        for (Event e : se.steps) collectNames(e, coref);

        // Pass 2: build the normalised nodes.
        List<NNode> steps = new ArrayList<>();
        for (Event e : se.steps) steps.add(node(e, coref));

        String searchMode = searchMode(se, dsl);
        return new NQuery(steps, searchMode, selectWhere(se));
    }

    private static void collectNames(Event e, Map<String, Integer> coref) {
        if (e instanceof AndEvent a) { for (Event c : a.children()) collectNames(c, coref); return; }
        if (e instanceof OrEvent o)  { for (Event c : o.children()) collectNames(c, coref); return; }
        // leaf: subject then (for PASS_TO) the receiver payload
        if (e.subject() instanceof Player p) coref.computeIfAbsent(p.name(), k -> coref.size());
        Object payload = e.action() == null ? null : e.action().payload;
        if (payload instanceof Player rp) coref.computeIfAbsent(rp.name(), k -> coref.size());
    }

    private static NNode node(Event e, Map<String, Integer> coref) {
        if (e instanceof AndEvent a) return group("AND", a.isNegated, a.children(), coref);
        if (e instanceof OrEvent o)  return group("OR",  o.isNegated, o.children(), coref);

        Action action = e.action();
        String type = action == null ? "?" : action.name;
        Object payload = action == null ? null : action.payload;

        List<NEntity> entities = new ArrayList<>();
        entities.add(entity(e.subject(), coref));
        if (payload instanceof Player) entities.add(entity((Player) payload, coref));

        List<Integer> params = payload instanceof Player ? List.of() : intsOf(payload);
        return new NLeaf(type, e.isNegated, e.isMinrange, entities, params, eventWhere(e));
    }

    private static NNode group(String kind, boolean negated, List<Event> children, Map<String, Integer> coref) {
        List<NNode> kids = new ArrayList<>();
        for (Event c : children) kids.add(node(c, coref));
        return new NGroup(kind, negated, kids);
    }

    private static NEntity entity(Entity subject, Map<String, Integer> coref) {
        if (subject instanceof Player p) {
            return new NEntity("PLAYER", coref.getOrDefault(p.name(), -1), p.id(), p.team(), null, null);
        }
        if (subject instanceof Ball) {
            return new NEntity("BALL", -1, null, null, null, null);
        }
        if (subject instanceof Team t) {
            List<Integer> ids = new ArrayList<>();
            for (Player p : t.players()) if (p.id() != null) ids.add(p.id());
            Collections.sort(ids);
            return new NEntity("TEAM", -1, null, null, t.name(), ids);
        }
        return new NEntity("NONE", -1, null, null, null, null);
    }

    private static List<Integer> intsOf(Object payload) {
        if (payload instanceof int[] arr) {
            List<Integer> out = new ArrayList<>(arr.length);
            for (int v : arr) out.add(v);
            return out;
        }
        if (payload instanceof Integer i) return List.of(i);
        return List.of();
    }

    private static String searchMode(SelectExpr se, String dsl) {
        if (COUNT.matcher(dsl).find()) return "COUNT";
        return switch (se.searchMode) {
            case -1 -> "DEFAULT_ALL";
            case 0  -> "ALL";
            case 1  -> "FIRST_1";
            default -> "FIRST_" + se.searchMode;
        };
    }

    private static Map<String, Integer> selectWhere(SelectExpr se) {
        Map<String, Integer> m = new TreeMap<>();
        put(m, "startMin", se.startMin);   put(m, "startMax", se.startMax);
        put(m, "endMin", se.endMin);       put(m, "endMax", se.endMax);
        put(m, "minDuration", se.minDuration); put(m, "maxDuration", se.maxDuration);
        put(m, "duringFrom", se.duringFrom); put(m, "duringTo", se.duringTo);
        put(m, "radius", se.radius);      put(m, "xcenter", se.xcenter);   put(m, "ycenter", se.ycenter);
        put(m, "xtop", se.xtop);           put(m, "ytop", se.ytop);
        put(m, "w", se.w);                 put(m, "h", se.h);
        put(m, "atLeast", se.atLeast);     put(m, "atMost", se.atMost);
        return m;
    }

    private static Map<String, Integer> eventWhere(Event e) {
        Map<String, Integer> m = new TreeMap<>();
        put(m, "startMin", e.timeStartMin); put(m, "startMax", e.timeStartMax);
        put(m, "endMin", e.timeEndMin);     put(m, "endMax", e.timeEndMax);
        put(m, "minDuration", e.minDuration); put(m, "maxDuration", e.maxDuration);
        put(m, "duringFrom", e.duringFrom); put(m, "duringTo", e.duringTo);
        put(m, "radius", e.radius);        put(m, "xcenter", e.xcenter); put(m, "ycenter", e.ycenter);
        put(m, "xtop", e.xtop);             put(m, "ytop", e.ytop);
        put(m, "w", e.w);                   put(m, "h", e.h);
        return m;
    }

    private static void put(Map<String, Integer> m, String k, int v) {
        if (v != -1) m.put(k, v);
    }

    // ------------------------------------------------------------------ //
    // Diffing (report the FIRST divergence, top-down)
    // ------------------------------------------------------------------ //

    private static Diff diff(NQuery gold, NQuery gen) {
        if (gold.steps().size() != gen.steps().size()) {
            return new Diff(Category.STRUCTURE,
                    "different number of top-level events: gold=" + gold.steps().size()
                            + " generated=" + gen.steps().size());
        }
        for (int i = 0; i < gold.steps().size(); i++) {
            Diff d = diffNode(gold.steps().get(i), gen.steps().get(i), "event #" + (i + 1));
            if (d != null) return d;
        }
        if (!gold.searchMode().equals(gen.searchMode())) {
            return new Diff(Category.SEARCHMODE,
                    "search mode: gold=" + gold.searchMode() + " generated=" + gen.searchMode());
        }
        if (!gold.selectWhere().equals(gen.selectWhere())) {
            return new Diff(Category.WHERE,
                    "SELECT WHERE constraints: gold=" + gold.selectWhere() + " generated=" + gen.selectWhere());
        }
        return new Diff(Category.EQUAL, "structurally identical");
    }

    private static Diff diffNode(NNode a, NNode b, String path) {
        boolean aGroup = a instanceof NGroup, bGroup = b instanceof NGroup;
        if (aGroup != bGroup) {
            return new Diff(Category.COMBINATOR, path + ": one side is "
                    + (aGroup ? ((NGroup) a).kind() : "a plain event")
                    + ", the other is "
                    + (bGroup ? ((NGroup) b).kind() : "a plain event"));
        }
        if (aGroup) return diffGroup((NGroup) a, (NGroup) b, path);
        return diffLeaf((NLeaf) a, (NLeaf) b, path);
    }

    private static Diff diffGroup(NGroup a, NGroup b, String path) {
        if (!a.kind().equals(b.kind())) {
            return new Diff(Category.COMBINATOR, path + ": " + a.kind() + " vs " + b.kind());
        }
        if (a.negated() != b.negated()) {
            return new Diff(Category.EVENT, path + ": negation differs (NOT)");
        }
        if (a.children().size() != b.children().size()) {
            return new Diff(Category.STRUCTURE, path + ": " + a.kind() + " has "
                    + a.children().size() + " vs " + b.children().size() + " operands");
        }
        for (int i = 0; i < a.children().size(); i++) {
            Diff d = diffNode(a.children().get(i), b.children().get(i), path + "." + a.kind() + "[" + (i + 1) + "]");
            if (d != null) return d;
        }
        return null;
    }

    private static Diff diffLeaf(NLeaf a, NLeaf b, String path) {
        if (!a.type().equals(b.type())) {
            return new Diff(Category.EVENT, path + ": event type " + a.type() + " vs " + b.type());
        }
        if (a.negated() != b.negated()) {
            return new Diff(Category.EVENT, path + " (" + a.type() + "): negation differs (NOT)");
        }
        if (a.minrange() != b.minrange()) {
            return new Diff(Category.MINRANGE, path + " (" + a.type() + "): .MINRANGE() present on "
                    + (a.minrange() ? "gold only" : "generated only"));
        }
        if (a.entities().size() != b.entities().size()) {
            return new Diff(Category.ENTITY, path + " (" + a.type() + "): "
                    + a.entities().size() + " vs " + b.entities().size() + " entities");
        }
        for (int i = 0; i < a.entities().size(); i++) {
            Diff d = diffEntity(a.entities().get(i), b.entities().get(i), path + " (" + a.type() + ")");
            if (d != null) return d;
        }
        if (!a.params().equals(b.params())) {
            return new Diff(Category.EVENT, path + " (" + a.type() + "): parameters (zones/K) "
                    + a.params() + " vs " + b.params());
        }
        if (!a.where().equals(b.where())) {
            return new Diff(Category.WHERE, path + " (" + a.type() + "): per-event WHERE "
                    + a.where() + " vs " + b.where());
        }
        return null;
    }

    private static Diff diffEntity(NEntity a, NEntity b, String path) {
        if (!a.kind().equals(b.kind())) {
            return new Diff(Category.ENTITY, path + ": entity kind " + a.kind() + " vs " + b.kind());
        }
        switch (a.kind()) {
            case "PLAYER" -> {
                if (!Objects.equals(a.id(), b.id())) {
                    return new Diff(Category.ENTITY, path + ": player jersey number "
                            + a.id() + " vs " + b.id());
                }
                if (!Objects.equals(a.team(), b.team())) {
                    return new Diff(Category.ENTITY, path + ": player team side "
                            + a.team() + " vs " + b.team());
                }
                if (a.coref() != b.coref()) {
                    return new Diff(Category.ENTITY, path + ": coreference differs (gold links this "
                            + "to player #" + a.coref() + ", generated to player #" + b.coref()
                            + ") — the two queries disagree on whether this is the same player as elsewhere");
                }
            }
            case "TEAM" -> {
                if (!Objects.equals(a.teamName(), b.teamName())) {
                    return new Diff(Category.ENTITY, path + ": team " + a.teamName() + " vs " + b.teamName());
                }
                if (!Objects.equals(a.teamIds(), b.teamIds())) {
                    return new Diff(Category.ENTITY, path + ": team members " + a.teamIds() + " vs " + b.teamIds());
                }
            }
            default -> { /* BALL / NONE: nothing to compare */ }
        }
        return null;
    }
}
