package org.main.sn.experiments;

import org.main.sn.dsl.Factory;
import org.main.sn.dsl.SelectExpr;
import org.main.sn.logic.GameStateReconstructionInstance;
import org.main.sn.logic.Query;
import org.main.sn.logic.Result;
import org.main.sn.web.QueryEvaluator;

import java.io.File;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Scalability benchmark of the paper. Two experiments, each run with the three queries of
 * {@link #QUERIES}, which are derived from the examples of the DSL section:
 * <ul>
 *   <li>{@code games}: the first i clips of train/test/valid (sorted by name) are searched
 *       together, for i = 1..maxGames, as when they are listed in one FROM;</li>
 *   <li>{@code long}: every instance of {@code data/SoccerNet/gamestate-2024/scalability}, by
 *       increasing length. These are SNGS-061 repeated, made by scalability_generator.py.</li>
 * </ul>
 * Loading the JSON and solving (model + search) are timed separately, after one warm-up run of
 * each query. The results are written as CSV files in the output directory.
 * <p>
 * Usage: {@code Scalability [games|long|all] [outputDir] [maxGames]}, by default
 * {@code all data/scalability 90}. A per-instance time limit can be set with
 * {@code -Dsn.timeLimitMs=<ms>}.
 */
public class Scalability {

    private static final String DATA = "data/SoccerNet/gamestate-2024";

    /** name, DSL query (without FROM): Example 1, Example 2, then Example 2 + Example 3 + Example 4's team event. */
    private static final String[][] QUERIES = {
            {"easy", "SELECT(BALL().MOVETO(6, 7).MINRANGE())"},
            {"intermediate", "SELECT(AND(PLAYER(\"p11\", 11).PASSTO(PLAYER(\"wc1\")).MINRANGE(), "
                    + "NOT(POSITION(BALL(), 1))))"},
            {"hard", "SELECT(AND(PLAYER(\"p11\", 11).PASSTO(PLAYER(\"wc1\")).MINRANGE(), NOT(POSITION(BALL(), 1))), "
                    + "POSSESSION(PLAYER(\"wc1\")).WHERE(RADIUS(-25, 10, 15)), "
                    + "TEAM(\"left\").MOVETO(1, 7, 2).MINRANGE())"},
    };

    private static final String HEADER = "query,answers,load_ms,model_ms,search_ms,solve_ms,nodes,failures,timeouts,error";

    public static void main(String[] args) throws Exception {
        String experiment = args.length > 0 ? args[0] : "all";
        Path out = Path.of(args.length > 1 ? args[1] : "data/scalability");
        int maxGames = args.length > 2 ? Integer.parseInt(args[2]) : 90;
        Files.createDirectories(out);

        warmUp();
        if (experiment.equals("games") || experiment.equals("all")) games(out.resolve("scalability_games.csv"), maxGames);
        if (experiment.equals("long") || experiment.equals("all")) longInstances(out.resolve("scalability_long.csv"));
    }

    /** Runs every query once on SNGS-061, so that JIT compilation is not timed. */
    private static void warmUp() throws Exception {
        GameStateReconstructionInstance clip = new GameStateReconstructionInstance(DATA + "/train/SNGS-061");
        for (String[] q : QUERIES) solve(q[1], List.of(clip));
    }

    /** The first i clips searched together, i = 1..maxGames; load_ms is the time to load these i clips. */
    private static void games(Path csv, int maxGames) throws Exception {
        List<String> clips = new ArrayList<>();
        for (String split : List.of("train", "test", "valid")) {
            String[] names = new File(DATA + "/" + split).list((dir, name) -> new File(dir, name).isDirectory());
            if (names != null) for (String name : names) clips.add(split + "/" + name);
        }
        clips.sort((a, b) -> a.substring(a.indexOf('/')).compareTo(b.substring(b.indexOf('/'))));
        int n = Math.min(maxGames, clips.size());

        List<GameStateReconstructionInstance> loaded = new ArrayList<>();
        long[] loadMs = new long[n + 1]; // loadMs[i]: time to load the first i clips
        for (int i = 0; i < n; i++) {
            long t = System.nanoTime();
            loaded.add(new GameStateReconstructionInstance(DATA + "/" + clips.get(i)));
            loadMs[i + 1] = loadMs[i] + (System.nanoTime() - t) / 1_000_000;
        }

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("games," + HEADER);
            for (String[] q : QUERIES) {
                for (int i = 1; i <= n; i++) {
                    String row = i + "," + q[0] + "," + solve(q[1], loaded.subList(0, i)).csv(loadMs[i]);
                    w.println(row);
                    w.flush();
                    System.out.println("games " + row);
                }
            }
        }
    }

    /** Every instance of the scalability folder, by increasing number of frames. */
    private static void longInstances(Path csv) throws Exception {
        String[] sizes = new File(DATA + "/scalability").list((dir, name) -> new File(dir, name).isDirectory());
        if (sizes == null || sizes.length == 0) {
            throw new IllegalStateException("No instance in " + DATA + "/scalability: run scalability_generator.py first.");
        }
        Arrays.sort(sizes, (a, b) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b)));

        try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(csv))) {
            w.println("frames," + HEADER);
            for (String size : sizes) {
                long t = System.nanoTime();
                GameStateReconstructionInstance instance = new GameStateReconstructionInstance(DATA + "/scalability/" + size);
                long load = (System.nanoTime() - t) / 1_000_000;
                for (String[] q : QUERIES) {
                    String row = instance.n + "," + q[0] + "," + solve(q[1], List.of(instance)).csv(load);
                    w.println(row);
                    w.flush();
                    System.out.println("long " + row);
                }
            }
        }
    }

    private record Run(int answers, long modelMs, long searchMs, long solveMs, Query.Effort effort, String error) {
        String csv(long loadMs) {
            String e = error == null ? "" : "\"" + error.replace("\"", "'") + "\"";
            return answers + "," + loadMs + "," + modelMs + "," + searchMs + "," + solveMs + ","
                    + effort.nodes() + "," + effort.failures() + "," + effort.timeouts() + "," + e;
        }
    }

    /** Builds the query on the given instances and solves it; a query that throws is recorded, not rethrown. */
    private static Run solve(String dsl, List<GameStateReconstructionInstance> instances) throws Exception {
        Factory.reset(); // player names must not carry over from the previous query
        SelectExpr se = (SelectExpr) QueryEvaluator.evaluate(dsl);
        se.matches.addAll(instances);
        Query query = new Query();
        long t = System.nanoTime();
        try {
            List<Result> results = query.apply(se);
            Query.Effort e = query.effort();
            return new Run(results.size(), e.modelNanos() / 1_000_000, e.searchNanos() / 1_000_000,
                    (System.nanoTime() - t) / 1_000_000, e, null);
        } catch (RuntimeException ex) {
            return new Run(-1, 0, 0, (System.nanoTime() - t) / 1_000_000, query.effort(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }
}
