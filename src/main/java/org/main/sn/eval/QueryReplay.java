package org.main.sn.eval;

import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;
import org.main.sn.logic.GameStateReconstructionInstance;
import org.main.sn.logic.Query;
import org.main.sn.logic.Result;
import org.main.sn.web.QueryEvaluator;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Replays the queries of a query-pairs file (the gold {@code dsl} of each entry) on games, and
 * writes one CSV row per (query, game): the number of answers, a digest of the answer set, the
 * solver effort and the time. Two builds are compared by running each and diffing the CSVs
 * ({@code data/eval/tools/compare_replays.py}); to replay an older build, put its classes first on
 * the classpath, before this class's.
 * <p>
 * Each game is loaded once. With {@code repeats > 1} each run is repeated and the fastest time is
 * kept. A query that does not build is reported with its error.
 * <p>
 * Usage: {@code QueryReplay <query_pairs.json> <dataset: all | SNGS-060,SNGS-061 | ...> <out.csv>
 * [repeats] [id-filter regex]}; a dataset token with a '/' is an instance folder, e.g.
 * {@code data/SoccerNet/gamestate-2024/scalability/7500}. A time limit per game can be set with
 * {@code -D}{@value Query#TIME_LIMIT_PROPERTY}{@code =<ms>}; a run that hits it is flagged
 * {@code timeouts > 0}, and its answers are then partial.
 */
public class QueryReplay {

    private record Run(int answers, String digest, Query.Effort effort, long wallNanos) {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("usage: QueryReplay <query_pairs.json> <dataset> <out.csv> [repeats] [id-filter regex]");
            System.exit(2);
        }
        List<Map<String, Object>> pairs = readPairs(args[0]);
        // a token with a '/' is an instance folder, loaded as it is (e.g. a scalability instance)
        List<String> games = new ArrayList<>();
        StringBuilder named = new StringBuilder();
        for (String token : args[1].split(",")) {
            if (token.contains("/")) games.add(token.trim());
            else named.append(named.isEmpty() ? "" : ",").append(token.trim());
        }
        if (!named.isEmpty()) games.addAll(SelectExpr.gamesOf(named.toString()));
        int repeats = args.length > 3 ? Integer.parseInt(args[3]) : 1;
        String filter = args.length > 4 ? args[4] : ".*";
        pairs.removeIf(p -> !String.valueOf(p.get("id")).matches(filter));
        System.err.printf("%d queries x %d games, %d repeat(s)%n", pairs.size(), games.size(), repeats);

        try (PrintWriter out = new PrintWriter(args[2], StandardCharsets.UTF_8)) {
            out.println("query_id,level,game,answers,digest,nodes,failures,model_ms,search_ms,wall_ms,timeouts,error");
            long t0 = System.nanoTime();
            for (int g = 0; g < games.size(); g++) {
                String game = games.get(g);
                GameStateReconstructionInstance instance = load(game);
                for (Map<String, Object> pair : pairs) {
                    String id = String.valueOf(pair.get("id"));
                    String dsl = withFrom(String.valueOf(pair.get("dsl")));
                    Object level = pair.get("level");
                    Run best = null;
                    String error = "";
                    try {
                        for (int r = 0; r < repeats; r++) {
                            Run run = run(dsl, instance);
                            if (best == null || run.wallNanos() < best.wallNanos()) best = run;
                        }
                    } catch (Exception | StackOverflowError e) {
                        error = (e.getClass().getSimpleName() + ": " + e.getMessage()).replaceAll("[,\\n\\r\"]", " ");
                    }
                    if (best == null) {
                        out.printf("%s,%s,%s,,,,,,,,,%s%n", id, level, game, error);
                    } else {
                        Query.Effort e = best.effort();
                        out.printf(Locale.ROOT, "%s,%s,%s,%d,%s,%d,%d,%.3f,%.3f,%.3f,%d,%n", id, level, game,
                                best.answers(), best.digest(), e.nodes(), e.failures(), e.modelNanos() / 1e6,
                                e.searchNanos() / 1e6, best.wallNanos() / 1e6, e.timeouts());
                    }
                }
                out.flush();
                System.err.printf("game %d/%d %s done, %.0f s%n", g + 1, games.size(), game, (System.nanoTime() - t0) / 1e9);
            }
        }
    }

    private static List<Map<String, Object>> readPairs(String path) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pairs = new ObjectMapper().readValue(new File(path), List.class);
        return new ArrayList<>(pairs);
    }

    private static GameStateReconstructionInstance load(String game) throws Exception {
        if (game.contains("/")) return new GameStateReconstructionInstance(game);
        Factory.reset();
        GameStateReconstructionInstance instance = Factory.SELECT(Factory.BALL().MOVETO(1, 2)).FROM(game).matches.getFirst();
        Factory.reset();
        return instance;
    }

    /** The query with a FROM after its SELECT(...), so that it parses without loading anything. */
    private static String withFrom(String dsl) {
        int s = dsl.indexOf("SELECT(");
        int depth = 0;
        for (int i = s + 6; i < dsl.length(); i++) {
            char c = dsl.charAt(i);
            if (c == '(') depth++;
            else if (c == ')' && --depth == 0) return dsl.substring(0, i + 1) + ".FROM(\"none\")" + dsl.substring(i + 1);
        }
        throw new IllegalArgumentException("no SELECT(...) in " + dsl);
    }

    private static Run run(String dsl, GameStateReconstructionInstance instance) throws Exception {
        Factory.reset();
        SelectExpr se = QueryEvaluator.parseStructure(dsl);
        se.matches.add(instance);
        Query query = new Query();
        long t0 = System.nanoTime();
        List<Result> results = query.apply(se);
        long wall = System.nanoTime() - t0;
        return new Run(results.size(), digest(results), query.effort(), wall);
    }

    /** A digest of the answer set: the answers' text without their solution numbers, sorted. */
    private static String digest(List<Result> results) throws Exception {
        List<String> answers = new ArrayList<>();
        for (Result r : results) {
            List<String> lines = r.toFormattedStrings();
            answers.add(String.join("|", lines.subList(2, lines.size())));
        }
        Collections.sort(answers);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        for (String a : answers) md.update((a + "\n").getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(md.digest()).substring(0, 16);
    }
}
