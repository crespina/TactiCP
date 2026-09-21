package org.main.sn.eval;

import org.main.sn.dsl.Factory;
import org.main.sn.dsl.LlmQuery;
import org.main.sn.dsl.SelectExpr;
import org.main.sn.logic.Result;
import org.main.sn.web.QueryEvaluator;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * End-to-end evaluation of the natural-language → DSL translator.
 *
 * <p>For each (natural-language query, gold DSL) pair in an input JSON file the
 * pipeline:
 * <ol>
 *   <li>translates the NL query with {@link LlmQuery} using a configurable model, temperature and retry count;</li>
 *   <li>if the generated DSL is textually identical to the gold DSL apart from its
 *       {@code FROM} clause (and insignificant whitespace), records the pair as an
 *       {@code identical_query} PASS <em>without running anything</em> — they are
 *       trivially equivalent;</li>
 *   <li>otherwise forces both the gold DSL and the generated DSL onto the same
 *       dataset (default {@code "all"}), since the dataset is supplied separately in
 *       the webapp and is not part of what the LLM must translate;</li>
 *   <li>runs both queries <em>one game at a time</em> and checks they produce the
 *       <em>same output</em> on every game (strict, order-independent match-set
 *       equality; COUNT compared by value), <strong>stopping at the first game
 *       whose outputs differ</strong> — once a single game disagrees the queries
 *       are provably not equivalent, so the remaining games are skipped;</li>
 *   <li>on any failure, diffs the two ASTs with {@link AstAnalyzer} to localise
 *       where the generated query diverges (Entity / Event / Combinator / …).</li>
 * </ol>
 * Every pair becomes one row in an output CSV, <em>flushed as soon as that pair
 * finishes</em>, and a summary is printed at the end. Because rows are durable the
 * moment they are produced, a run that dies half way through can be picked up again
 * with {@code --resume}.
 *
 * <p>Usage:
 * <pre>
 *   --input &lt;pairs.json&gt;        (required) JSON array of {nl, dsl} objects
 *   --output &lt;results.csv&gt;      default eval_results.csv
 *   --model &lt;groq-model-id&gt;     default llama-3.3-70b-versatile
 *   --temperature &lt;double&gt;      default 0.0
 *   --max-retries &lt;int&gt;         default 3
 *   --eval-dataset &lt;string&gt;     default "all" (what BOTH queries run on)
 *   --timeout-seconds &lt;int&gt;     default 120 per query, per game (0 = no limit)
 *   --limit &lt;int&gt;               only evaluate the first N pairs
 *   --identity                     skip the LLM and use the gold DSL as the
 *                                  "generated" one (self-test of the harness)
 *   --judge                        also record a second LLM instance's 0/1 opinion
 *   --judge-model &lt;id&gt;       model for that second instance (implies --judge)
 *   --resume                       reuse the rows already in --output instead of
 *                                  re-evaluating those pairs
 *   --redo &lt;STATUS,…&gt;           with --resume, re-run rows with these statuses
 * </pre>
 */
public final class EvaluationPipeline {

    // ------------------------------------------------------------------ //
    // Configuration
    // ------------------------------------------------------------------ //

    // ------------------------------------------------------------------ //
    // The judge — a second LLM instance, asked for a 0/1 opinion
    // ------------------------------------------------------------------ //

    /**
     * What the judge is told. It is a separate {@link LlmQuery} instance with its
     * own conversation: this prompt and one user message are everything it ever
     * sees. It is not told that another model produced the query, it never sees the
     * gold DSL, the translator's prompt, its retries or its errors, and its answer
     * has no effect on pass/fail — that still comes from running both queries.
     */
    private static final String JUDGE_PROMPT = """
            You review translations of natural-language questions about football
            (soccer) tracking data into a pattern-matching DSL. Your only job is to
            say whether a given query faithfully expresses a given request.

            ## The DSL, in brief

            SELECT(event, ...).WHERE(constraint, ...).search_mode

            The queries you are shown have NO .FROM(...) clause, and that is correct and
            expected: the games to search are chosen separately and are no part of the
            translation. A missing .FROM(...) is never a fault. If a query does happen to
            carry one, ignore it.

            Entities:
              PLAYER("name") | PLAYER("name", jersey) | PLAYER("name", "left"|"right")
              PLAYER("name", jersey, "left"|"right")  — the order is (name, jersey, team)
              BALL()
              TEAM("left") | TEAM("right")            — a whole side, always lowercase
              TEAM("name", jersey, ...) | TEAM("name", PLAYER(...), ...)  — a custom team,
                                                         which must always list its members
              The same player identifier used twice means the same player both times.

            Events:
              PLAYER("p").PASSTO(PLAYER("q"))         — a pass
              PLAYER("p").MOVETO(from_zone, to_zone)  — a player changes zone
              BALL().MOVETO(from_zone, to_zone)       — the ball changes zone
              TEAM("t").MOVETO(from_zone, to_zone, k) — at least k players of the team do
              POSSESSION(PLAYER("p")) | POSSESSION(TEAM("t"))    — has the ball
              POSITION(PLAYER("p")|BALL()|TEAM("t"), zone, ...)  — is in one of those zones
              POSITION(TEAM("t"), K(k), zone, ...)    — at least k team players are
              .MINRANGE() asks for the minimal interval, and belongs on PASSTO and MOVETO.

            Combinators:
              AND(a, b, ...) — the events hold AT THE SAME TIME (their intervals overlap)
              OR(a, b, ...)  — at least one holds
              NOT(a)         — does not hold; valid only on POSSESSION and POSITION
              AND and OR are never nested inside one another.

            Time:
              Events written as separate comma-separated arguments of SELECT happen ONE
              AFTER ANOTHER, in the order given ("then", "after", "followed by").
              AND(...) is for events that COINCIDE ("while", "as", "at the same time").

            Zones are integers 1..14.

            WHERE, on a single event or on the whole SELECT:
              STARTMIN/STARTMAX/ENDMIN/ENDMAX(frame), MINDURATION/MAXDURATION(frames),
              RECTANGLE(xtop, ytop, w, h), RADIUS(cx, cy, r),
              ATLEAST(n)/ATMOST(n) — these two only on the whole SELECT.

            Search mode: .COUNT() (the last call in the query) | .SEARCH("all"|"first"|k).

            ## What to check

            The entities and jersey numbers, the events, the temporal reading (sequence
            vs simultaneous), the zones, the constraints and the search mode.

            Judge meaning, not style. Answer 1 whenever the query asks for what the
            request asks for — a plain request translated by the obvious short query is
            a good translation, and needs nothing added to it. Ignore the choice of
            identifier names. Do not demand detail the request never mentioned: no
            jersey number when it names no player, no WHERE when it states no
            constraint, no search mode when it asks for no particular number of
            results. Answer 0 only for a real defect: a different meaning from the
            request, something the request asked for that is missing, or DSL that
            breaks one of the rules above.

            ## How to answer

            Reply with a single character and nothing else:
              1 — the query is a good translation of the request
              0 — it is wrong, incomplete, or invalid
            No explanation, no punctuation, no markdown, no code. Just 0 or 1.
            """;

    /** First 0 or 1 in the judge's reply; -1 if it said neither. */
    static int verdictOf(String reply) {
        if (reply == null) return -1;
        for (int i = 0; i < reply.length(); i++) {
            char c = reply.charAt(i);
            if (c == '0' || c == '1') return c - '0';
        }
        return -1;
    }

    private static final class Config {
        Path input;
        Path output = Paths.get("eval_results.csv");
        String model = LlmQuery.DEFAULT_MODEL;
        double temperature = LlmQuery.DEFAULT_TEMPERATURE;
        int maxRetries = LlmQuery.DEFAULT_MAX_RETRIES;
        /** null = leave LlmQuery's default (Groq, or $LLM_ENDPOINT if set). */
        String endpoint = null;
        String evalDataset = "all";
        int timeoutSeconds = 120;
        int limit = -1;
        boolean identity = false;
        boolean resume = false;
        /** Statuses that --resume re-runs instead of reusing. CONFIG_ERROR always is. */
        final Set<String> redo = new TreeSet<>(Set.of("CONFIG_ERROR"));
        boolean judge = false;
        String judgeModel = null;   // null = judge with the translation model
    }

    public static void main(String[] args) throws Exception {
        Config cfg = parseArgs(args);
        if (cfg.input == null) {
            System.err.println("ERROR: --input <pairs.json> is required.\n");
            printUsage();
            System.exit(2);
        }

        List<Pair> pairs = readPairs(cfg.input);
        if (cfg.limit >= 0 && pairs.size() > cfg.limit) pairs = pairs.subList(0, cfg.limit);

        System.out.printf("Loaded %d pair(s) from %s%n", pairs.size(), cfg.input);
        System.out.printf("model=%s  temperature=%s  maxRetries=%d  evalDataset=\"%s\"  timeout=%ds  identity=%b  resume=%b%n%n",
                cfg.model, cfg.temperature, cfg.maxRetries, cfg.evalDataset, cfg.timeoutSeconds,
                cfg.identity, cfg.resume);

        LlmQuery llm = null;
        if (!cfg.identity) {
            llm = new LlmQuery()
                    .model(cfg.model)
                    .temperature(cfg.temperature)
                    .maxRetries(cfg.maxRetries)
                    .endpoint(cfg.endpoint);
            System.out.printf("endpoint=%s%n%n", llm.endpoint());
        }

        // A second, independent instance. It holds no conversation with the one
        // above and is only ever given JUDGE_PROMPT plus a single request/query
        // message, so the two know nothing of each other.
        LlmQuery judge = null;
        if (cfg.judge) {
            judge = new LlmQuery()
                    .model(cfg.judgeModel != null ? cfg.judgeModel : cfg.model)
                    .temperature(0.0)
                    .endpoint(cfg.endpoint);
            System.out.printf("judge=%s%n%n", judge.model());
        }

        Map<String, Integer> statusCounts = new TreeMap<>();
        Map<String, Integer> categoryCounts = new TreeMap<>();
        int identicalCount = 0;
        int resumedCount = 0;
        int[] judgeCounts = new int[3];   // {said good, said not good, agreed with the run}

        // Rows are appended (and flushed) one at a time, so the CSV is always a
        // complete record of the pairs finished so far — which is what --resume reads.
        ResultWriter writer;
        try {
            writer = ResultWriter.open(cfg, pairs);
        } catch (IOException e) {
            System.err.println("ERROR: " + e.getMessage());
            System.exit(2);
            return;
        }

        try (ResultWriter w = writer) {
            for (int i = 0; i < pairs.size(); i++) {
                Pair p = pairs.get(i);
                System.out.printf("[%d/%d] %s%n", i + 1, pairs.size(), truncate(p.nl, 90));

                String[] row = w.reusableRowFor(p);
                if (row != null) {
                    resumedCount++;
                    System.out.printf("      -> %s  [resumed from %s]%n",
                            row[IDX_STATUS], cfg.output.getFileName());
                } else {
                    RowResult r = evaluatePair(p, llm, judge, cfg);
                    row = r.toCsv(cfg);
                    w.writeRow(row);
                    String note = r.identicalQuery
                            ? "  [identical to gold (ignoring FROM) — dataset not run]"
                            : (r.failCategory == null ? "" : "  [" + r.failCategory + "] " + truncate(r.failDetail, 100));
                    System.out.printf("      -> %s%s%n", r.overallStatus, note);
                }

                // Tally from the row itself so resumed and freshly computed pairs
                // are counted identically.
                statusCounts.merge(row[IDX_STATUS], 1, Integer::sum);
                if ("true".equals(row[IDX_IDENTICAL])) identicalCount++;
                String category = row[IDX_FAIL_CATEGORY];
                if (category != null && !category.isBlank())
                    categoryCounts.merge(category, 1, Integer::sum);

                String verdict = row[IDX_JUDGE];
                if (!verdict.isBlank()) {
                    boolean good = "1".equals(verdict);
                    boolean passed = "PASS".equals(row[IDX_STATUS]);
                    judgeCounts[good ? 0 : 1]++;
                    if (good == passed) judgeCounts[2]++;
                }
            }
        }

        printSummary(pairs.size(), statusCounts, categoryCounts, identicalCount, resumedCount,
                judgeCounts, cfg.output);
    }

    // ------------------------------------------------------------------ //
    // Per-pair evaluation
    // ------------------------------------------------------------------ //

    private static RowResult evaluatePair(Pair p, LlmQuery llm, LlmQuery judge, Config cfg) {
        RowResult r = new RowResult();
        r.id = p.id;
        r.nl = p.nl;
        r.goldDsl = p.dsl;

        // 1) Translate (or, in identity mode, reuse the gold DSL).
        if (cfg.identity) {
            r.translationSuccess = true;
            r.attemptsUsed = 0;
            r.generatedDsl = p.dsl;
        } else {
            long t0 = System.nanoTime();
            try {
                Factory.reset();
                LlmQuery.TranslationResult tr = llm.translateDetailed(p.nl, cfg.evalDataset);
                r.translationSuccess = tr.success();
                r.attemptsUsed = tr.attemptsUsed();
                r.generatedDsl = tr.dsl() == null ? "" : tr.dsl();
                r.translationError = tr.error();
            } catch (Exception e) {
                r.translationSuccess = false;
                r.translationError = "translate call failed: " + e.getMessage();
            }
            r.translateMs = msSince(t0);
        }

        // If translation failed (or produced nothing) there is no generated query
        // to compare against the gold one — record that and stop here.
        if (!r.translationSuccess || r.generatedDsl.isBlank()) {
            r.genStatus = "SKIPPED";
            r.overallStatus = "TRANSLATION_FAILED";
            r.failCategory = "TRANSLATION";
            r.failDetail = (r.translationError == null || r.translationError.isBlank())
                    ? "no valid DSL produced" : r.translationError;
            return r;
        }

        // 1b) Second opinion: a separate LLM instance is shown only the request and
        //     the query, and answers 0 or 1. Purely recorded — it decides nothing.
        if (judge != null) {
            try {
                r.judgeVerdict = verdictOf(judge.ask(JUDGE_PROMPT,
                        "Request:\n" + r.nl + "\n\nDSL query:\n" + r.generatedDsl
                                + "\n\nGood translation? Answer 0 or 1."));
            } catch (Exception e) {
                System.err.println("WARN: judge failed for pair " + p.id + ": " + e.getMessage());
            }
        }

        // 2) Cheapest check first: if the generated query is textually identical to
        //    the gold one apart from its FROM clause (which the eval supplies
        //    separately) and insignificant whitespace, they are trivially
        //    equivalent — there is nothing to gain from touching the dataset.
        //    Skipped in identity mode, whose whole purpose is to exercise execution.
        if (!cfg.identity
                && canonicalIgnoringFrom(r.goldDsl).equals(canonicalIgnoringFrom(r.generatedDsl))) {
            r.identicalQuery = true;
            r.outputsMatch = true;
            r.overallStatus = "PASS";
            r.gamesTotal = 0;
            r.gamesChecked = 0;
            return r;
        }

        // 3) Compare the two queries game by game, stopping at the first game
        //    whose outputs differ (or where either query errors / times out).
        //    Once a single game disagrees the queries are provably NOT equivalent,
        //    so the remaining games would only cost runtime without changing the
        //    verdict. Running per game (rather than once over the whole dataset)
        //    also resets the Factory between games, avoiding identifier bleed.
        List<String> games;
        try {
            games = SelectExpr.gamesOf(cfg.evalDataset);
        } catch (Exception e) {
            r.overallStatus = "CONFIG_ERROR";
            r.failCategory = "CONFIG";
            r.failDetail = "could not enumerate games for dataset \"" + cfg.evalDataset + "\": " + e.getMessage();
            return r;
        }
        r.gamesTotal = games.size();
        if (games.isEmpty()) {
            r.overallStatus = "CONFIG_ERROR";
            r.failCategory = "CONFIG";
            r.failDetail = "dataset \"" + cfg.evalDataset + "\" resolved to no games";
            return r;
        }

        long goldMsTotal = 0, genMsTotal = 0;
        int goldCountTotal = 0, genCountTotal = 0;
        String kind = "";
        for (int gi = 0; gi < games.size(); gi++) {
            String game = games.get(gi);
            r.gamesChecked = gi + 1;

            ExecOutput gold = runWithTimeout(withDataset(r.goldDsl, game), cfg.timeoutSeconds);
            goldMsTotal += Math.max(0, gold.ms);

            boolean goldOk = "OK".equals(gold.status);
            ExecOutput gen = null;
            if (goldOk) {
                gen = runWithTimeout(withDataset(r.generatedDsl, game), cfg.timeoutSeconds);
                genMsTotal += Math.max(0, gen.ms);
            }

            // A game is "decisive" if it settles equivalence one way or the other:
            // a broken reference, a generated error/timeout, or differing outputs.
            boolean decisive = !goldOk
                    || "TIMEOUT".equals(gen.status)
                    || "ERROR".equals(gen.status)
                    || !outputsEqual(gold, gen);

            if (decisive) {
                fillGold(r, gold);
                fillGen(r, gen);
                r.goldMs = goldMsTotal;
                r.genMs = genMsTotal;
                r.mismatchGame = game;
                classify(r, gold, gen, cfg.evalDataset);
                // Make it explicit which game revealed the difference.
                r.failDetail = "game " + game + " (" + r.gamesChecked + "/" + r.gamesTotal + "): "
                        + (r.failDetail == null ? "" : r.failDetail);
                return r;
            }

            // Clean match on this game: accumulate totals for the PASS row.
            kind = gold.kind;
            goldCountTotal += gold.count;
            genCountTotal += gen.count;
        }

        // Every game agreed → the queries are equivalent over the whole dataset.
        r.goldStatus = "OK";
        r.genStatus = "OK";
        r.goldKind = kind;
        r.genKind = kind;
        r.goldCount = goldCountTotal;
        r.genCount = genCountTotal;
        r.goldMs = goldMsTotal;
        r.genMs = genMsTotal;
        r.outputsMatch = true;
        r.overallStatus = "PASS";
        return r;
    }

    /** Copies a gold query's per-game output into the row. */
    private static void fillGold(RowResult r, ExecOutput gold) {
        r.goldStatus = gold.status;
        r.goldKind = gold.kind;
        r.goldCount = gold.count;
        r.goldError = gold.error;
    }

    /** Copies a generated query's per-game output into the row (null = not run). */
    private static void fillGen(RowResult r, ExecOutput gen) {
        if (gen == null) {
            r.genStatus = "SKIPPED";
            return;
        }
        r.genStatus = gen.status;
        r.genKind = gen.kind;
        r.genCount = gen.count;
        r.genError = gen.error;
    }

    /**
     * AST diff of gold vs generated. Both are given the eval dataset's FROM first,
     * because the pairs no longer carry one and {@code parseStructure} requires it;
     * the FROM is ignored by the diff, so the injected dataset does not matter.
     */
    private static AstAnalyzer.Diff astDiff(RowResult r, String evalDataset) {
        return AstAnalyzer.analyze(withDataset(r.goldDsl, evalDataset),
                                   withDataset(r.generatedDsl, evalDataset));
    }

    /** Fills in outputsMatch, overallStatus, failCategory, failDetail. */
    private static void classify(RowResult r, ExecOutput gold, ExecOutput gen, String evalDataset) {
        if (!r.translationSuccess) {
            r.overallStatus = "TRANSLATION_FAILED";
            r.failCategory = "TRANSLATION";
            r.failDetail = r.translationError == null ? "no valid DSL produced" : r.translationError;
            return;
        }
        if (!"OK".equals(gold.status)) {
            // The reference query itself did not run — this is a problem with the
            // gold data, not with the translation. Flag it distinctly.
            r.overallStatus = "GOLD_ERROR";
            r.failCategory = "GOLD";
            r.failDetail = "gold query " + gold.status + ": " + gold.error;
            return;
        }
        if ("TIMEOUT".equals(gen.status)) {
            r.overallStatus = "TIMEOUT";
            AstAnalyzer.Diff d = astDiff(r, evalDataset);
            r.failCategory = d.isEqual() ? "EXECUTION" : d.category().name();
            r.failDetail = "generated query timed out | " + d.detail();
            return;
        }
        if ("ERROR".equals(gen.status)) {
            r.overallStatus = "EXEC_ERROR";
            AstAnalyzer.Diff d = astDiff(r, evalDataset);
            r.failCategory = d.isEqual() ? "EXECUTION" : d.category().name();
            r.failDetail = "runtime error: " + gen.error + " | " + d.detail();
            return;
        }
        // Both ran: strict output comparison.
        boolean match = outputsEqual(gold, gen);
        r.outputsMatch = match;
        if (match) {
            r.overallStatus = "PASS";
        } else {
            r.overallStatus = "OUTPUT_MISMATCH";
            AstAnalyzer.Diff d = astDiff(r, evalDataset);
            if (d.isEqual()) {
                r.failCategory = "OUTPUT_ONLY";
                r.failDetail = "ASTs are structurally identical but outputs differ "
                        + "(likely a coreference/semantic nuance); "
                        + "gold=" + describe(gold) + " generated=" + describe(gen);
            } else {
                r.failCategory = d.category().name();
                r.failDetail = d.detail() + " | gold=" + describe(gold) + " generated=" + describe(gen);
            }
        }
    }

    private static String describe(ExecOutput o) {
        return "COUNT".equals(o.kind) ? ("count=" + o.count) : (o.count + " matches");
    }

    /** Strict, order-independent match-set equality (COUNT compared by value). */
    private static boolean outputsEqual(ExecOutput a, ExecOutput b) {
        if (!Objects.equals(a.kind, b.kind)) return false;
        if ("COUNT".equals(a.kind)) return a.count == b.count;
        // MATCHES: same multiset of per-match signatures (both already sorted).
        return a.signatures.equals(b.signatures);
    }

    // ------------------------------------------------------------------ //
    // Query execution
    // ------------------------------------------------------------------ //

    private static final class ExecOutput {
        String status;                 // OK / ERROR / TIMEOUT
        String kind = "";              // MATCHES / COUNT
        int count = -1;
        List<String> signatures = List.of();
        String error = "";
        long ms = -1;
    }

    /** Runs one DSL query with an optional per-query wall-clock timeout. */
    private static ExecOutput runWithTimeout(String dsl, int timeoutSeconds) {
        long t0 = System.nanoTime();
        if (timeoutSeconds <= 0) {
            ExecOutput o = execute(dsl);
            o.ms = msSince(t0);
            return o;
        }
        ExecutorService ex = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = new Thread(runnable, "eval-query");
            t.setDaemon(true);   // never block JVM shutdown on a runaway solver
            return t;
        });
        try {
            Future<ExecOutput> f = ex.submit(() -> execute(dsl));
            ExecOutput o;
            try {
                o = f.get(timeoutSeconds, TimeUnit.SECONDS);
            } catch (TimeoutException te) {
                f.cancel(true);
                o = new ExecOutput();
                o.status = "TIMEOUT";
                o.error = "exceeded " + timeoutSeconds + "s";
            } catch (ExecutionException ee) {
                o = errorOutput(ee.getCause() == null ? ee : ee.getCause());
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                o = errorOutput(ie);
            }
            o.ms = msSince(t0);
            return o;
        } finally {
            ex.shutdownNow();
        }
    }

    /** Evaluates and runs a DSL query, capturing its observable output. */
    private static ExecOutput execute(String dsl) {
        ExecOutput o = new ExecOutput();
        try {
            Factory.reset();  // isolate identifier canonicalisation per query
            Object evaluated = QueryEvaluator.evaluate(dsl);
            if (evaluated instanceof Integer count) {
                o.status = "OK";
                o.kind = "COUNT";
                o.count = count;
                return o;
            }
            SelectExpr expr = (SelectExpr) evaluated;
            List<Result> results = expr.search();
            o.status = "OK";
            o.kind = "MATCHES";
            o.count = results.size();
            o.signatures = results.stream()
                    .map(EvaluationPipeline::signature)
                    .sorted()
                    .collect(Collectors.toList());
            return o;
        } catch (Throwable t) {
            return errorOutput(t);
        }
    }

    private static ExecOutput errorOutput(Throwable t) {
        ExecOutput o = new ExecOutput();
        o.status = "ERROR";
        o.error = t.getClass().getSimpleName() + ": " + t.getMessage();
        return o;
    }

    // ------------------------------------------------------------------ //
    // Canonical signature of one match (instance + event tree + intervals + ids)
    // ------------------------------------------------------------------ //

    private static String signature(Result r) {
        StringBuilder sb = new StringBuilder(r.instance).append("||");
        for (Result.ResultEvent e : r.events) appendEventSig(sb, e);
        return sb.toString();
    }

    private static void appendEventSig(StringBuilder sb, Result.ResultEvent e) {
        String neg = e.negated ? "!" : "";
        if (e instanceof Result.GroupEvent ge) {
            sb.append(ge.groupKind).append('(');
            for (Result.ResultEvent c : ge.children) appendEventSig(sb, c);
            sb.append(')').append(itv(e)).append(';');
        } else if (e instanceof Result.PassEvent pe) {
            sb.append("PASS").append(neg).append(pe.passerId).append('>').append(pe.receiverId).append(itv(e)).append(';');
        } else if (e instanceof Result.PossessionEvent ps) {
            sb.append("POSS").append(neg).append(sorted(ps.playerIds)).append(itv(e)).append(';');
        } else if (e instanceof Result.PlayerMoveEvent mv) {
            sb.append("PMOVE").append(neg).append(mv.playerId).append(itv(e)).append(';');
        } else if (e instanceof Result.TeamMoveEvent tm) {
            sb.append("TMOVE").append(neg).append(sorted(tm.playerIds)).append(itv(e)).append(';');
        } else if (e instanceof Result.PositionEvent pos) {
            sb.append("POS").append(neg).append(sorted(pos.playerIds)).append(itv(e)).append(';');
        } else if (e instanceof Result.BallMoveEvent) {
            sb.append("BMOVE").append(neg).append(itv(e)).append(';');
        } else {
            sb.append(e.type).append(neg).append(itv(e)).append(';');
        }
    }

    private static String itv(Result.ResultEvent e) {
        if (e.interval == null) return "@[?]";
        return "@[" + e.interval.start + "," + e.interval.end + "," + e.interval.length + "]";
    }

    private static String sorted(List<Integer> ids) {
        return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(",", "{", "}"));
    }

    // ------------------------------------------------------------------ //
    // Dataset rewriting
    // ------------------------------------------------------------------ //

    private static final Pattern FROM = Pattern.compile("\\.FROM\\s*\\(\\s*\"[^\"]*\"\\s*\\)");

    /**
     * Canonical form of a DSL query for an "are these the same query?" comparison
     * that deliberately ignores the {@code FROM} clause (which the eval supplies
     * separately) and insignificant whitespace: the {@code .FROM("…")} clause is
     * removed and every whitespace character <em>outside</em> a double-quoted
     * string literal is dropped. So
     * {@code SELECT(BALL().MOVETO(4, 1).MINRANGE()).FROM("all")} and
     * {@code SELECT(BALL().MOVETO(4,1).MINRANGE())} canonicalise identically, while
     * whitespace inside names (e.g. {@code PLAYER("p 1")}) is preserved.
     */
    static String canonicalIgnoringFrom(String dsl) {
        if (dsl == null) return "";
        String noFrom = FROM.matcher(dsl).replaceAll("");
        StringBuilder sb = new StringBuilder(noFrom.length());
        boolean inString = false;
        for (int i = 0; i < noFrom.length(); i++) {
            char c = noFrom.charAt(i);
            if (inString) {
                sb.append(c);
                if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
                sb.append(c);
            } else if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Makes a query run on {@code dataset}, exactly as the webapp injects the FROM
     * clause from the games the user selected (the pairs themselves are not
     * expected to specify a game):
     * <ul>
     *   <li>if the query already has a {@code .FROM("…")}, its dataset is replaced;</li>
     *   <li>if it has none, a {@code .FROM("dataset")} is inserted right after the
     *       {@code SELECT(...)} block — i.e. before any {@code .WHERE(...)},
     *       {@code .SEARCH(...)} or {@code .COUNT()}, which is where the grammar
     *       requires it.</li>
     * </ul>
     */
    static String withDataset(String dsl, String dataset) {
        if (dsl == null) return null;
        Matcher m = FROM.matcher(dsl);
        if (m.find()) {
            return m.replaceAll(Matcher.quoteReplacement(".FROM(\"" + dataset + "\")"));
        }
        int insertAt = endOfSelect(dsl);
        if (insertAt < 0) return dsl;   // no SELECT( — evaluate() will report it
        return dsl.substring(0, insertAt) + ".FROM(\"" + dataset + "\")" + dsl.substring(insertAt);
    }

    /**
     * Returns the index immediately after the closing parenthesis of the leading
     * {@code SELECT(...)}, or -1 if there is no balanced {@code SELECT(...)}.
     * String literals are skipped so parentheses inside names never mislead the scan.
     */
    private static int endOfSelect(String dsl) {
        int s = dsl.indexOf("SELECT(");
        if (s < 0) return -1;
        int depth = 0;
        boolean inString = false;
        for (int i = s + "SELECT".length(); i < dsl.length(); i++) {
            char c = dsl.charAt(i);
            if (inString) {
                if (c == '"') inString = false;
            } else if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                if (--depth == 0) return i + 1;
            }
        }
        return -1;   // unbalanced
    }

    // ------------------------------------------------------------------ //
    // Input / output
    // ------------------------------------------------------------------ //

    private record Pair(String id, String nl, String dsl) {}

    @SuppressWarnings("unchecked")
    private static List<Pair> readPairs(Path input) throws IOException {
        String content = Files.readString(input);
        Object parsed = new ObjectMapper().readValue(content, Object.class);

        List<?> rawList;
        if (parsed instanceof List<?> list) {
            rawList = list;
        } else if (parsed instanceof Map<?, ?> map) {
            Object arr = firstNonNull(map, "pairs", "queries", "data", "items");
            if (!(arr instanceof List<?> list)) {
                throw new IOException("Top-level JSON object must contain a \"pairs\" array.");
            }
            rawList = list;
        } else {
            throw new IOException("Input JSON must be an array of {nl, dsl} objects.");
        }

        List<Pair> pairs = new ArrayList<>();
        int ordinal = 0;
        for (Object o : rawList) {
            ordinal++;
            if (!(o instanceof Map<?, ?> row)) continue;
            Map<String, Object> m = (Map<String, Object>) row;
            String nl  = asString(firstNonNull(m, "nl", "query", "human", "natural_language", "input", "question"));
            String dsl = asString(firstNonNull(m, "dsl", "gold", "translation", "target", "expected", "output"));
            String id  = asString(firstNonNull(m, "id", "name", "index"));
            if (id == null) id = String.valueOf(ordinal);
            if (nl == null || dsl == null) {
                System.err.printf("WARN: skipping row %d (missing nl or dsl): %s%n", ordinal, m.keySet());
                continue;
            }
            pairs.add(new Pair(id, nl, dsl));
        }
        if (pairs.isEmpty()) throw new IOException("No usable (nl, dsl) pairs found in " + input);
        return pairs;
    }

    private static Object firstNonNull(Map<?, ?> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return v;
        }
        return null;
    }

    private static String asString(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static final String[] HEADER = {
            "id", "nl_query", "gold_dsl", "generated_dsl",
            "overall_status", "outputs_match", "fail_category", "fail_detail",
            "translation_success", "attempts_used", "translation_error",
            "gold_status", "gold_kind", "gold_count",
            "gen_status", "gen_kind", "gen_count",
            "gold_error", "gen_error",
            "translate_ms", "gold_ms", "gen_ms",
            "model", "temperature", "max_retries", "eval_dataset",
            "identical_query", "games_total", "games_checked", "mismatch_game",
            "judge_verdict"
    };

    private static final int IDX_ID             = column("id");
    private static final int IDX_NL             = column("nl_query");
    private static final int IDX_GOLD_DSL       = column("gold_dsl");
    private static final int IDX_GENERATED_DSL  = column("generated_dsl");
    private static final int IDX_STATUS         = column("overall_status");
    private static final int IDX_FAIL_CATEGORY  = column("fail_category");
    private static final int IDX_IDENTICAL      = column("identical_query");
    private static final int IDX_MODEL          = column("model");
    private static final int IDX_TEMPERATURE    = column("temperature");
    private static final int IDX_MAX_RETRIES    = column("max_retries");
    private static final int IDX_EVAL_DATASET   = column("eval_dataset");
    private static final int IDX_JUDGE          = column("judge_verdict");

    private static int column(String name) {
        for (int i = 0; i < HEADER.length; i++) if (HEADER[i].equals(name)) return i;
        throw new IllegalStateException("No such CSV column: " + name);
    }

    /**
     * Streams result rows to the output CSV, flushing after every row, and — under
     * {@code --resume} — hands back the rows an earlier run already produced so
     * those pairs are not evaluated again.
     *
     * <p>A stored row is reused only if it describes <em>the same work</em>: same
     * {@code id}, natural-language query and gold DSL (so an edited corpus is
     * re-run rather than silently reported with stale numbers), same model,
     * temperature, retry count and eval dataset, a non-blank status, and a status
     * the caller did not ask to redo. Note that {@code --identity} is not recorded
     * in the CSV, so an identity self-test and a real run must not share an output
     * file.
     *
     * <p>When every stored row is reusable the file is simply appended to. If any
     * row is stale — a changed pair, a different configuration, a duplicate, a pair
     * no longer in the input, or one being redone — the file is rewritten from the
     * rows that survive, so the CSV never ends up holding two rows for one pair.
     */
    private static final class ResultWriter implements Closeable {

        private final BufferedWriter out;
        private final Map<String, String[]> reusable;

        private ResultWriter(BufferedWriter out, Map<String, String[]> reusable) {
            this.out = out;
            this.reusable = reusable;
        }

        static ResultWriter open(Config cfg, List<Pair> pairs) throws IOException {
            if (cfg.output.getParent() != null) Files.createDirectories(cfg.output.getParent());

            Map<String, String[]> reusable = new LinkedHashMap<>();
            List<String[]> keep = new ArrayList<>();
            boolean rewrite = true;

            if (cfg.resume && Files.exists(cfg.output)) {
                List<String[]> rows = parseCsv(Files.readString(cfg.output, StandardCharsets.UTF_8));
                if (!rows.isEmpty()) {
                    if (!Arrays.equals(rows.get(0), HEADER)) {
                        throw new IOException("--resume: " + cfg.output + " has a different set of columns "
                                + "(it predates the current CSV layout). Move or delete it, then re-run.");
                    }
                    Set<String> wanted = new HashSet<>();
                    for (Pair p : pairs) wanted.add(keyOf(p.id(), p.nl(), p.dsl()));

                    boolean stale = false;
                    for (String[] row : rows.subList(1, rows.size())) {
                        String key = row.length == HEADER.length
                                ? keyOf(row[IDX_ID], row[IDX_NL], row[IDX_GOLD_DSL]) : null;
                        if (key != null && wanted.contains(key) && !reusable.containsKey(key)
                                && sameConfig(row, cfg) && isComplete(row, cfg)) {
                            reusable.put(key, row);
                            keep.add(row);
                        } else {
                            stale = true;
                        }
                    }
                    rewrite = stale;
                    System.out.printf("Resuming from %s: %d row(s) reusable, %d to (re-)evaluate%s%n",
                            cfg.output, reusable.size(), pairs.size() - reusable.size(),
                            stale ? " (file rewritten: it held stale or duplicate rows)" : "");
                }
            } else if (cfg.resume) {
                System.out.printf("Resuming from %s: no previous results, starting fresh%n", cfg.output);
            }

            BufferedWriter out;
            if (rewrite) {
                out = Files.newBufferedWriter(cfg.output, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                writeRow(out, HEADER);
                for (String[] row : keep) writeRow(out, row);
            } else {
                // Header and the kept rows are already on disk — just append.
                out = Files.newBufferedWriter(cfg.output, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
            out.flush();
            return new ResultWriter(out, reusable);
        }

        /** The stored row for this pair, or {@code null} if it must be evaluated. */
        String[] reusableRowFor(Pair p) {
            return reusable.get(keyOf(p.id(), p.nl(), p.dsl()));
        }

        void writeRow(String[] row) throws IOException {
            writeRow(out, row);
            out.flush();   // a row is durable as soon as it is produced
        }

        @Override public void close() throws IOException {
            out.close();
        }

        private static void writeRow(BufferedWriter out, String[] row) throws IOException {
            for (int i = 0; i < row.length; i++) {
                if (i > 0) out.write(',');
                out.write(csv(row[i]));
            }
            out.write('\n');
        }

        private static String keyOf(String id, String nl, String goldDsl) {
            return id + ' ' + nl + ' ' + goldDsl;
        }

        /** The row must come from a run configured exactly like this one. */
        private static boolean sameConfig(String[] row, Config cfg) {
            return row[IDX_MODEL].equals(cfg.model)
                    && row[IDX_TEMPERATURE].equals(String.valueOf(cfg.temperature))
                    && row[IDX_MAX_RETRIES].equals(String.valueOf(cfg.maxRetries))
                    && row[IDX_EVAL_DATASET].equals(cfg.evalDataset);
        }

        /** A row is reusable only if it reached a verdict the caller is not redoing. */
        private static boolean isComplete(String[] row, Config cfg) {
            String status = row[IDX_STATUS];
            if (status == null || status.isBlank() || cfg.redo.contains(status)) return false;
            // Asking for the judge on a run whose rows predate it would otherwise
            // leave judge_verdict half empty. Rows with no generated DSL never get
            // a verdict, so they stay reusable.
            return !cfg.judge || !row[IDX_JUDGE].isBlank() || row[IDX_GENERATED_DSL].isBlank();
        }
    }

    /** RFC 4180 reader: handles quoted fields containing commas, quotes and newlines. */
    static List<String[]> parseCsv(String content) {
        List<String[]> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        boolean started = false;   // this line has at least one (possibly empty) field

        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (inQuotes) {
                if (c != '"') {
                    cur.append(c);
                } else if (i + 1 < content.length() && content.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    inQuotes = false;
                }
            } else if (c == '"') {
                inQuotes = true;
                started = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
                started = true;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < content.length() && content.charAt(i + 1) == '\n') i++;
                if (started || cur.length() > 0) {
                    fields.add(cur.toString());
                    rows.add(fields.toArray(new String[0]));
                }
                fields.clear();
                cur.setLength(0);
                started = false;
            } else {
                cur.append(c);
                started = true;
            }
        }
        if (started || cur.length() > 0) {
            fields.add(cur.toString());
            rows.add(fields.toArray(new String[0]));
        }
        return rows;
    }

    private static String csv(String v) {
        if (v == null) v = "";
        boolean needsQuote = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        if (!needsQuote) return v;
        return '"' + v.replace("\"", "\"\"") + '"';
    }

    private static void printSummary(int total, Map<String, Integer> statusCounts,
                                     Map<String, Integer> categoryCounts, int identical,
                                     int resumed, int[] judgeCounts, Path output) {
        int pass = statusCounts.getOrDefault("PASS", 0);
        System.out.println("\n=========================== SUMMARY ===========================");
        System.out.printf("Total pairs      : %d%n", total);
        if (resumed > 0)
            System.out.printf("   of which reused from a previous run: %d%n", resumed);
        System.out.printf("Passed           : %d (%.1f%%)%n", pass, total == 0 ? 0.0 : 100.0 * pass / total);
        if (identical > 0)
            System.out.printf("   of which identical to gold (not run): %d%n", identical);
        System.out.println("By status:");
        statusCounts.forEach((k, v) -> System.out.printf("   %-20s %d%n", k, v));
        if (!categoryCounts.isEmpty()) {
            System.out.println("Failures by AST/category (where the generated query diverges):");
            categoryCounts.forEach((k, v) -> System.out.printf("   %-20s %d%n", k, v));
        }
        int judged = judgeCounts[0] + judgeCounts[1];
        if (judged > 0) {
            System.out.printf("Judge (opinion only, decides nothing): good %d, not good %d%n",
                    judgeCounts[0], judgeCounts[1]);
            System.out.printf("   agrees with the executed verdict: %d/%d (%.1f%%)%n",
                    judgeCounts[2], judged, 100.0 * judgeCounts[2] / judged);
        }
        System.out.println("CSV written to   : " + output.toAbsolutePath());
        System.out.println("===============================================================");
    }

    // ------------------------------------------------------------------ //
    // Row container
    // ------------------------------------------------------------------ //

    private static final class RowResult {
        String id, nl, goldDsl, generatedDsl = "";
        String overallStatus = "", failCategory, failDetail;
        boolean outputsMatch;
        boolean translationSuccess;
        int attemptsUsed;
        String translationError;
        String goldStatus = "", goldKind = "", goldError = "";
        String genStatus = "", genKind = "", genError = "";
        int goldCount = -1, genCount = -1;
        long translateMs = -1, goldMs = -1, genMs = -1;
        int gamesTotal = -1, gamesChecked = 0;
        String mismatchGame;
        boolean identicalQuery = false;
        int judgeVerdict = -1;   // 1 = good, 0 = not good, -1 = not asked / no answer

        String[] toCsv(Config cfg) {
            return new String[]{
                    id, nl, goldDsl, generatedDsl,
                    overallStatus, String.valueOf(outputsMatch),
                    failCategory == null ? "" : failCategory,
                    failDetail == null ? "" : failDetail,
                    String.valueOf(translationSuccess), String.valueOf(attemptsUsed),
                    translationError == null ? "" : translationError,
                    goldStatus, goldKind, num(goldCount),
                    genStatus, genKind, num(genCount),
                    goldError == null ? "" : goldError,
                    genError == null ? "" : genError,
                    num(translateMs), num(goldMs), num(genMs),
                    cfg.model, String.valueOf(cfg.temperature),
                    String.valueOf(cfg.maxRetries), cfg.evalDataset,
                    String.valueOf(identicalQuery),
                    num(gamesTotal), num(gamesChecked),
                    mismatchGame == null ? "" : mismatchGame,
                    num(judgeVerdict)
            };
        }

        private static String num(long v) { return v < 0 ? "" : String.valueOf(v); }
    }

    // ------------------------------------------------------------------ //
    // Small helpers
    // ------------------------------------------------------------------ //

    private static long msSince(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        s = s.replace("\n", " ");
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    private static Config parseArgs(String[] args) {
        Config c = new Config();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--input"           -> c.input = Paths.get(next(args, ++i, a));
                case "--output"          -> c.output = Paths.get(next(args, ++i, a));
                case "--model"           -> c.model = next(args, ++i, a);
                case "--endpoint"        -> c.endpoint = next(args, ++i, a);
                case "--local"           -> c.endpoint = LlmQuery.LOCAL_ENDPOINT;
                case "--temperature"     -> c.temperature = Double.parseDouble(next(args, ++i, a));
                case "--max-retries"     -> c.maxRetries = Integer.parseInt(next(args, ++i, a));
                case "--eval-dataset"    -> c.evalDataset = next(args, ++i, a);
                case "--timeout-seconds" -> c.timeoutSeconds = Integer.parseInt(next(args, ++i, a));
                case "--limit"           -> c.limit = Integer.parseInt(next(args, ++i, a));
                case "--identity"        -> c.identity = true;
                case "--resume"          -> c.resume = true;
                case "--judge"           -> c.judge = true;
                case "--judge-model"     -> { c.judgeModel = next(args, ++i, a); c.judge = true; }
                case "--redo"            -> {
                    for (String s : next(args, ++i, a).split(",")) {
                        String status = s.strip().toUpperCase(Locale.ROOT);
                        if (!status.isEmpty()) c.redo.add(status);
                    }
                }
                case "-h", "--help"      -> { printUsage(); System.exit(0); }
                default -> {
                    System.err.println("Unknown argument: " + a);
                    printUsage();
                    System.exit(2);
                }
            }
        }
        return c;
    }

    private static String next(String[] args, int i, String flag) {
        if (i >= args.length) {
            System.err.println("Missing value for " + flag);
            System.exit(2);
        }
        return args[i];
    }

    private static void printUsage() {
        System.out.println("""
                Usage: EvaluationPipeline --input <pairs.json> [options]

                  --input <path>            (required) JSON array of {"nl": ..., "dsl": ...} objects
                  --output <path>           CSV output file            (default: eval_results.csv)
                  --model <model-id>        translation model          (default: openai/gpt-oss-120b)
                  --endpoint <url>          OpenAI-compatible chat-completions URL
                                            (default: Groq, or $LLM_ENDPOINT if set)
                  --local                   shorthand for --endpoint
                                            http://localhost:8000/v1/chat/completions
                                            (a self-hosted model; needs no API key)
                  --temperature <double>    model temperature          (default: 0.0)
                  --max-retries <int>       translate attempts         (default: 3)
                  --eval-dataset <string>   dataset BOTH queries run on (default: "all")
                  --timeout-seconds <int>   per-query, per-game timeout, 0=none  (default: 120)
                  --limit <int>             evaluate only first N pairs
                  --identity                skip the LLM; use gold as generated (harness self-test)
                  --judge                   also ask a second, independent LLM instance whether each
                                            translation is good (1) or not (0); recorded in the
                                            judge_verdict column, never used to decide pass/fail
                  --judge-model <id>        model for that second instance (implies --judge;
                                            default: the same model as the translation)
                  --resume                  reuse the rows already in --output for pairs that were
                                            finished by an earlier run, and evaluate only the rest
                                            (a row is reused only if its id / NL / gold DSL and the
                                            model, temperature, retries and dataset all match)
                  --redo <STATUS,...>       with --resume, re-run pairs whose recorded status is one
                                            of these, e.g. --redo TRANSLATION_FAILED,TIMEOUT
                                            (CONFIG_ERROR is always re-run)
                """);
    }
}