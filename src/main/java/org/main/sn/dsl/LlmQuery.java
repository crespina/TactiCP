package org.main.sn.dsl;

import org.main.sn.web.QueryEvaluator;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

public class LlmQuery {

    public static final int    DEFAULT_MAX_RETRIES = 3;
    public static final String DEFAULT_MODEL       = "openai/gpt-oss-120b";
    public static final double DEFAULT_TEMPERATURE = 0.0;

    /** Groq's OpenAI-compatible chat endpoint — the default; unchanged behaviour. */
    public static final String GROQ_ENDPOINT    = "https://api.groq.com/openai/v1/chat/completions";
    /** A locally served model (vLLM, llama.cpp, …) speaking the same protocol. */
    public static final String LOCAL_ENDPOINT   = "http://localhost:8000/v1/chat/completions";

    public static final String DEFAULT_ENDPOINT = GROQ_ENDPOINT;

    private static final String SYSTEM_PROMPT = """
            You are an expert at translating natural language queries into a custom DSL for a \
            football (soccer) spatio-temporal pattern matching system.

            ## Entities

            ### Player
            - PLAYER("name")                        — anonymous wildcard player
            - PLAYER("name", jersey_num)            — player with jersey number (int)
            - PLAYER("name", "left")                — player from the left team
            - PLAYER("name", "right")               — player from the right team
            - PLAYER("name", jersey_num, "left")    — left-team player with that jersey number
            - PLAYER("name", jersey_num, "right")   — right-team player with that jersey number
              * The argument order is (name, jersey, team). PLAYER("p", "left", 7) does not exist.
              * The same identifier appearing twice refers to the same player performing both actions.
              * Whenever the request identifies a player, give the jersey number — PLAYER("p7", 7),
                not PLAYER("p7"). Anonymous players make the search far slower and it may not finish.

            ### Ball
            - BALL()

            ### Team
            - TEAM("left")                              — all players of the left team in the match
            - TEAM("right")                             — all players of the right team in the match
            - TEAM("name", id1, id2, ...)               — team built from player jersey numbers
            - TEAM("name", PLAYER("p1"), PLAYER("p2"))  — team built from player entities
              * "left" and "right" MUST be lowercase. TEAM("LEFT") is not recognised as a side: it
                builds an EMPTY team, which silently matches nothing or fails.
              * A custom team MUST always be given its members. TEAM("t1") on its own is an empty
                team and the query fails — only "left" and "right" name a whole side by themselves.
              * Unlike a player identifier, a team identifier is not remembered across events. If the
                same custom team is used in two events, repeat its full member list each time.

            ## Events

            ### Labeled events (span multiple frames; use .MINRANGE() to get minimal interval)
            - PLAYER("p").PASSTO(PLAYER("q"))              — p passes to q (both must be PLAYER)
            - PLAYER("p").MOVETO(from_zone, to_zone)       — player moves from zone to zone
            - BALL().MOVETO(from_zone, to_zone)            — ball moves from zone to zone
            - TEAM("t").MOVETO(from_zone, to_zone, k)      — at least k players of team move zone→zone
                                                             (always exactly three arguments)

            ### Atomic events (hold at a single frame; usable with NOT)
            - POSSESSION(PLAYER("p"))                      — player has the ball
            - POSSESSION(TEAM("t"))                        — any player of the team has the ball
            - POSITION(PLAYER("p"), zone1, zone2, ...)     — player is in one of the listed zones
            - POSITION(BALL(), zone1, zone2, ...)          — ball is in one of the listed zones
            - POSITION(TEAM("t"), zone1, zone2, ...)       — at least 1 team player is in those zones
            - POSITION(TEAM("t"), K(k), zone1, zone2, ...) — at least k team players are in those zones
              * POSSESSION takes a PLAYER or a TEAM only — never POSSESSION(BALL()).
              * POSITION always needs at least one zone.
              * K(k) is used only in POSITION(TEAM(...), K(k), ...) — never for a player or the ball.

            ## Zones
            Zones are integers 1..14. There is no zone 0 and nothing above 14. Seen from above, with
            x running from the left goal (-62) to the right goal (+62) and y from the bottom
            touchline (-39) to the top touchline (+39):
              1, 2, 3, 4  — the four central quadrants: 1 upper-left, 2 upper-right,
                            3 lower-right, 4 lower-left
              6, 7        — top band, left and right of the halfway line
              11, 10      — bottom band, left and right of the halfway line
              13, 14      — the far-left and far-right bands, i.e. the two goal areas
              5, 8        — the outer upper corners, left and right
              12, 9       — the outer lower corners, left and right

            ## Event modifier
            - event.MINRANGE()
              With MINRANGE: minimal contiguous interval where the predicate holds (e.g. ball-in-flight for a pass).
              Without MINRANGE: largest possession-based interval.
              MINRANGE is required on PASSTO and MOVETO. It is also allowed on POSSESSION and
              POSITION, and on an AND/OR, when the request asks for the minimal such interval.

            ## Combinators
            - AND(event1, event2, ...)   — all events hold SIMULTANEOUSLY, i.e. their intervals overlap at the same time. AND says nothing about order: it is ONLY for co-occurring events, NEVER for events that happen one after another (see "Temporal semantics" below).
            - OR(event1, event2, ...)    — at least one event holds
            - NOT(event)                 — event does NOT hold (complement of intervals)

            Rules for combinators — all four matter:
            - NOT is only valid for atomic events: POSSESSION and POSITION. NOT(PASSTO(...)) and
              NOT(MOVETO(...)) are rejected when the query runs, and NOT(AND(...)) / NOT(OR(...)) do
              not negate the group at all — they negate each member separately, which is never what
              was asked. To say "A holds and B does not", write AND(A, NOT(B)) with B atomic.
            - AND and OR must NOT contain another AND or OR. Their arguments may only be atomic
              events, labeled events, or NOT(atomic). A nested combinator makes the query fail.
              When the request needs several groups, give each group its own argument of SELECT:
              SELECT(AND(a, b), OR(c, d)) is valid; SELECT(AND(OR(a, b), c)) is not.
            - Give an AND or an OR at least two arguments.
            - Keep the branches of an OR of the same kind and as specific as possible. An OR that
              mixes POSSESSION with another kind of event, or mixes a plain event with a NOT, can
              take forever; an OR over two passes, two moves or two positions is fine.

            ## Full query structure
            SELECT(event1, event2, ...).FROM("dataset").WHERE(constraint, ...).search_mode
            If .COUNT() is used it must be the very last call in the query.

            ## Temporal semantics — SEQUENCE vs SIMULTANEOUS
            How you combine events decides when they happen relative to one another.

            - SEQUENCE (events happen one after another, in chronological order):
              list the events as separate, comma-separated arguments of SELECT, written in the
              order they occur in time. The first argument happens first, then the next, and so on.
                  SELECT(eventA, eventB)         — eventA happens, and afterwards eventB happens
              The intervals do NOT overlap: eventB is expected to occur after eventA.

            - SIMULTANEOUS (events happen at the same time):
              wrap the events in AND(...). Their intervals must overlap / intersect. They hold
              together at the same moment, with no implied order.
                  SELECT(AND(eventA, eventB))    — eventA and eventB hold at the SAME time

            Decide which one from the wording of the request:
            - Sequence / chronological  → comma-separated events in SELECT (NOT AND).
              Signalled by words like: "then", "and then", "after", "afterwards", "followed by",
              "next", "leads to", "first ... then ...". ("before X" also implies order — put the
              earlier event first.)
            - Simultaneous / overlapping → AND(...).
              Signalled by words like: "at the same time", "while", "as", "during",
              "simultaneously", "when ... is also ...", "together with".

            Rule of thumb: AND = same time. A comma-separated list in SELECT = one after another.
            Never use AND to express ordering, and never rely on the comma sequence for events that
            must coincide.

            ### FROM (required)
            "all" | "train" | "test" | "valid" | "SNGS-XXX" | "SNGS-060,SNGS-061"
            Every clip is exactly 750 frames long (30 seconds at 25 frames per second), so frame
            numbers belong to 0..750 and durations to 1..750.

            ### WHERE (optional)
            WHERE can be applied in two places:

            1. On a single event, to constrain only that event:
                 event.WHERE(constraint, ...)
               Example: SELECT(eventA.WHERE(MAXDURATION(25)), eventB)
               This restricts only that event's interval, not the whole sequence. It works on any
               event — including AND(...).WHERE(...), OR(...).WHERE(...), NOT(...).WHERE(...) and an
               event written inside an AND or OR — and it may be placed before or after MINRANGE().
               Each event of a sequence may carry its own WHERE.

            2. On the full SELECT expression, to constrain the whole matched sequence:
                 SELECT(...).FROM("...").WHERE(constraint, ...)
               Example: SELECT(eventA, eventB).WHERE(STARTMIN(100), ENDMAX(500))
               This restricts the overall start/end/duration/location of the entire match. It may be
               written more than once, and before or after the search mode.

            ATLEAST and ATMOST are only valid on the full SELECT (not on individual events).

            Temporal constraints (time in frames, 1 frame = 1/25 s, a clip is 750 frames):
              STARTMIN(n)    — the match must start at or after frame n
              STARTMAX(n)    — the match must start at or before frame n
              ENDMIN(n)      — the match must end at or after frame n
              ENDMAX(n)      — the match must end at or before frame n
              MINDURATION(n) — the match must last at least n frames
              MAXDURATION(n) — the match must last at most n frames

            Spatial constraints (whole numbers of metres, in the same frame as the zones: the pitch
            is centred on (0,0), x from -62 to +62, y from -39 to +39):
              RECTANGLE(xtop, ytop, width, height) — the match must stay inside this rectangle for
                its whole duration. (xtop, ytop) is the TOP-LEFT corner: x runs from xtop rightwards
                to xtop+width, and y runs DOWNWARDS from ytop to ytop-height. So the whole pitch is
                RECTANGLE(-62, 39, 125, 78), the upper left quarter is RECTANGLE(-62, 39, 62, 39),
                and a small box around the centre is RECTANGLE(-5, 5, 10, 10).
              RADIUS(cx, cy, r) — the match must stay within radius r of (cx, cy) for its whole
                duration.
              Use RECTANGLE or RADIUS, never both in the same query: when a RADIUS is present
              anywhere, every RECTANGLE is ignored.

            Aggregation constraints (only on full SELECT):
              ATLEAST(n) — raise an error if fewer than n occurrences are found in a clip
              ATMOST(n)  — raise an error if more than n occurrences are found in a clip

            ### Search mode (optional, default = all results)
            - .COUNT()         — return only the number of matches
            - .SEARCH("all")   — return all matches (explicit default)
            - .SEARCH("first") — return the first match only
            - .SEARCH(k)       — return the first k matches
              The integer forms .SEARCH(0) and .SEARCH(1) also exist and mean "all" and "first".

            ## Examples (natural language → DSL)

            Q: Find all passes from any player to any other player in game SNGS-060.
            A: SELECT(PLAYER("p1").PASSTO(PLAYER("p2")).MINRANGE()).FROM("SNGS-060")

            Q: Find every time the ball moves from zone 4 to zone 1 across all games, but only if there are at least 2 occurrences.
            A: SELECT(BALL().MOVETO(4, 1).MINRANGE()).FROM("all").WHERE(ATLEAST(2))

            Q: Find moments where player 11 passes to any player while player 3 does not have possession, in game SNGS-061.
            A: SELECT(AND(PLAYER("p11",11).PASSTO(PLAYER("p2")).MINRANGE(), NOT(POSSESSION(PLAYER("p3",3))))).FROM("SNGS-061")

            Q: Find all moments where player 7 does not have the ball in game SNGS-060.
            A: SELECT(NOT(POSSESSION(PLAYER("p7",7)))).FROM("SNGS-060")

            Q: Find player 13 moving from zone 6 to zone 7 in game SNGS-061, but only between frame 10 and frame 740.
            A: SELECT(PLAYER("p13",13).MOVETO(6,7).MINRANGE()).FROM("SNGS-061").WHERE(STARTMIN(10),ENDMAX(740))

            Q: How many times does player 1 pass to player 2 in game SNGS-060?
            A: SELECT(PLAYER("p1",1).PASSTO(PLAYER("p2",2)).MINRANGE()).FROM("SNGS-060").COUNT()

            Q: How many passes from player 11 to player 16 end before frame 400 in game SNGS-060?
            A: SELECT(PLAYER("p11",11).PASSTO(PLAYER("p16",16)).MINRANGE()).FROM("SNGS-060").WHERE(ENDMAX(400)).COUNT()

            Q: Find all moments where player 1 is in zone 6 or zone 7 in game SNGS-061.
            A: SELECT(POSITION(PLAYER("p1"),6,7)).FROM("SNGS-061")

            Q: Find the minimal intervals during which the ball is in zone 13 in game SNGS-060.
            A: SELECT(POSITION(BALL(),13).MINRANGE()).FROM("SNGS-060")

            Q: Find moments where player 13 moves from zone 6 to zone 7 at the same time as player 15 moves from zone 1 to zone 2, in game SNGS-061.
            A: SELECT(AND(PLAYER("p13",13).MOVETO(6,7).MINRANGE(), PLAYER("p15",15).MOVETO(1,2).MINRANGE())).FROM("SNGS-061")

            Q: Find the ball moving from zone 4 to zone 1, then from zone 14 to zone 9, in game SNGS-061.
            A: SELECT(BALL().MOVETO(4,1).MINRANGE(), BALL().MOVETO(14,9).MINRANGE()).FROM("SNGS-061")

            Q: Find all the times where player 11 passed to player 16 and then the ball moved from zone 3 to zone 4, in game SNGS-060.
            A: SELECT(PLAYER("p11",11).PASSTO(PLAYER("p16",16)).MINRANGE(), BALL().MOVETO(3, 4).MINRANGE()).FROM("SNGS-060")

            Q: Find all the times where player 11 passes to player 16 while the ball is moving from zone 3 to zone 4, in game SNGS-060.
            A: SELECT(AND(PLAYER("p11",11).PASSTO(PLAYER("p16",16)).MINRANGE(), BALL().MOVETO(3, 4).MINRANGE())).FROM("SNGS-060")

            Q: Find player 7 moving from zone 1 to zone 6 in at most 40 frames and then passing to player 9, in game SNGS-060.
            A: SELECT(PLAYER("p7",7).MOVETO(1,6).MINRANGE().WHERE(MAXDURATION(40)), PLAYER("p7",7).PASSTO(PLAYER("p9",9)).MINRANGE()).FROM("SNGS-060")

            Q: Find all moments where at least 3 players from the left team are in zones 4, 5, or 6 across all games.
            A: SELECT(POSITION(TEAM("left"), K(3), 4, 5, 6)).FROM("all")

            Q: Find moments where at least 2 of players 8, 9 and 10 are in zone 14 in game SNGS-060.
            A: SELECT(POSITION(TEAM("t1", 8, 9, 10), K(2), 14)).FROM("SNGS-060")

            Q: Find the first instance where at least 4 players from the left team move from zone 3 to zone 7 across all games.
            A: SELECT(TEAM("left").MOVETO(3, 7, 4).MINRANGE()).FROM("all").SEARCH("first")

            Q: Find moments where the right team has possession for at least 50 frames in game SNGS-060.
            A: SELECT(POSSESSION(TEAM("right"))).FROM("SNGS-060").WHERE(MINDURATION(50))

            Q: Find the first pass from a left-team player to a right-team player in the training split.
            A: SELECT(PLAYER("p1","left").PASSTO(PLAYER("p2","right")).MINRANGE()).FROM("train").SEARCH("first")

            Q: Find all moments where either player 7 has the ball or player 13 has the ball, in game SNGS-060.
            A: SELECT(OR(POSSESSION(PLAYER("p7",7)), POSSESSION(PLAYER("p13",13)))).FROM("SNGS-060")

            Q: Find the first 5 passes made by player 7 in game SNGS-060.
            A: SELECT(PLAYER("p7",7).PASSTO(PLAYER("q")).MINRANGE()).FROM("SNGS-060").SEARCH(5)

            Q: Find moments where the left team has possession, the ball is in zone 3 or 4, and player 9 is not in zone 13, in game SNGS-060.
            A: SELECT(AND(POSSESSION(TEAM("left")), POSITION(BALL(),3,4), NOT(POSITION(PLAYER("p9",9),13)))).FROM("SNGS-060")

            Q: Find passes that stay in the upper left part of the pitch in game SNGS-060.
            A: SELECT(PLAYER("p1").PASSTO(PLAYER("p2")).MINRANGE()).FROM("SNGS-060").WHERE(RECTANGLE(-62, 39, 62, 39))

            ## Output rules
            1. Output ONLY the DSL query on a single line — no markdown, no code fences, no explanation.
            2. The output MUST start with SELECT( and contain .FROM("...").
            3. Always append .MINRANGE() to PASSTO and MOVETO events.
            4. NEVER use NOT with PASSTO, MOVETO, AND, or OR.
            5. NEVER put an AND or an OR inside another AND or OR — flatten it, or make each group a
               separate argument of SELECT.
            6. A whole side is TEAM("left") or TEAM("right"), always lowercase.
            7. Zones are integers 1..14; frame numbers are 0..750.
            8. Give a player its jersey number whenever the request mentions one.
            9. If .COUNT() is used, it comes last.
            """;

    private final String apiKey;
    /** HTTP/2-capable; used for TLS endpoints, where h2 is negotiated by ALPN. */
    private final HttpClient http;
    /** Created on demand for cleartext endpoints — see {@link #clientFor(String)}. */
    private HttpClient httpPlain;

    // Per-instance configuration (defaults reproduce the original webapp behaviour).
    private String model       = DEFAULT_MODEL;
    private double temperature = DEFAULT_TEMPERATURE;
    private int    maxRetries  = DEFAULT_MAX_RETRIES;
    private String endpoint    = resolveEndpoint();

    public static void main(String[] args) throws IOException {
        LlmQuery llm = new LlmQuery();
        String humanQuery = args.length > 0 ? String.join(" ", args) : null;
        if (humanQuery == null || humanQuery.isBlank()) {
            System.out.print("Enter your query in natural language:\n> ");
            humanQuery = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
        }
        String dsl = llm.translate(humanQuery);
        System.out.println("\nGenerated DSL query:");
        System.out.println(dsl);

    }

    /**
     * Reads GROQ_API_KEY from the environment or from the nearest .env file.
     * <p>
     * A missing key is only an error for endpoints that need one (Groq), and is
     * reported on the first call rather than here — a locally served model needs
     * no key, so requiring one at construction would make {@link #endpoint(String)}
     * unreachable for users who have no Groq key at all.
     */
    public LlmQuery() {
        this.apiKey = resolveSetting("GROQ_API_KEY");
        this.http   = HttpClient.newHttpClient();
    }

    /** Use an explicit API key (useful for tests or programmatic setup). */
    public LlmQuery(String apiKey) {
        this.apiKey = apiKey;
        this.http   = HttpClient.newHttpClient();
    }

    // -------------------------------------------------------------------------
    // what uration (fluent). Defaults reproduce the original behaviour, so the
    // webapp — which never calls these — is unaffected.
    // -------------------------------------------------------------------------

    /** Groq model id, e.g. "llama-3.3-70b-versatile". */
    public LlmQuery model(String model) {
        if (model != null && !model.isBlank()) this.model = model;
        return this;
    }

    /** Sampling temperature passed to the model (0 = deterministic). */
    public LlmQuery temperature(double temperature) {
        this.temperature = temperature;
        return this;
    }

    /** Number of translate attempts before giving up (each retry feeds back the error). */
    public LlmQuery maxRetries(int maxRetries) {
        if (maxRetries >= 1) this.maxRetries = maxRetries;
        return this;
    }

    /**
     * OpenAI-compatible chat-completions URL to call. Defaults to Groq, or to
     * {@code LLM_ENDPOINT} when that is set in the environment or the .env file.
     * Pass the full path, e.g. {@code http://localhost:8000/v1/chat/completions}
     * for a locally served model. A {@code null} or blank value is ignored, so
     * {@code .endpoint(cfg.endpoint)} is safe when nothing was configured.
     */
    public LlmQuery endpoint(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) this.endpoint = endpoint.strip();
        return this;
    }

    public String model()       { return model; }
    public double temperature() { return temperature; }
    public int    maxRetries()  { return maxRetries; }
    public String endpoint()    { return endpoint; }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** A single translate attempt, captured for evaluation/diagnostics. */
    public record Attempt(int number, String rawResponse, String dsl, boolean valid, String error) {}

    /**
     * Rich result of a translation, exposing how many attempts were needed and
     * why any of them failed. Unlike {@link #translate(String, String)} this never
     * throws on translation failure — inspect {@link #success()} instead.
     */
    public record TranslationResult(boolean success, String dsl, int attemptsUsed,
                                    String error, List<Attempt> attempts) {}

    /**
     * Prompts the user interactively for the dataset, then translates the query.
     */
    public String translate(String humanQuery) throws IOException {
        System.out.print("Which dataset to search? (\"all\", \"train\", \"test\", \"valid\", or \"SNGS-XXX\")\n> ");
        String dataset = new BufferedReader(new InputStreamReader(System.in)).readLine().trim();
        return translate(humanQuery, dataset);
    }

    /**
     * Translates a natural-language query into a DSL string, validates it via
     * QueryEvaluator.evaluate(), and retries up to {@code maxRetries} times on failure.
     *
     * @param humanQuery natural-language description of the pattern
     * @param dataset    dataset to search ("all", "train", "SNGS-060", …)
     * @return a syntactically valid DSL query string
     * @throws IOException      if the Groq API call fails
     * @throws RuntimeException if the LLM cannot produce a valid query after {@code maxRetries} attempts
     */
    public String translate(String humanQuery, String dataset) throws IOException {
        TranslationResult result = translateDetailed(humanQuery, dataset);
        if (result.success()) return result.dsl();
        throw new RuntimeException(
                "LLM could not produce a valid DSL query after " + maxRetries + " attempts.\n" +
                "Last error: " + result.error());
    }

    /**
     * Same translation loop as {@link #translate(String, String)}, but returns a
     * {@link TranslationResult} instead of throwing on failure, and records every
     * attempt (raw response, extracted DSL, and syntax error if any). This is what
     * the evaluation pipeline uses so it can report the retry count and, on
     * failure, the last syntax error.
     */
    public TranslationResult translateDetailed(String humanQuery, String dataset) throws IOException {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msg("system", SYSTEM_PROMPT));
        messages.add(msg("user", "Dataset: \"" + dataset + "\"\nQuery: " + humanQuery));

        List<Attempt> attempts = new ArrayList<>();
        String lastError = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            if (attempt > 1) {
                messages.add(msg("user",
                        "The DSL query produced this error:\n" + lastError +
                        "\n\nOutput only the corrected DSL query."));
            }

            String raw = callGroq(messages);
            String dsl = extractDsl(raw);
            messages.add(msg("assistant", raw));

            try {
                // Syntax-only check: verifies the query parses/builds but does NOT load
                // game data or run the solver, so it stays fast even for FROM("all").
                QueryEvaluator.checkSyntax(dsl);
                attempts.add(new Attempt(attempt, raw, dsl, true, null));
                return new TranslationResult(true, dsl, attempt, null, attempts);
            } catch (Exception e) {
                lastError = e.getMessage();
                attempts.add(new Attempt(attempt, raw, dsl, false, lastError));
                System.out.println("[LlmQuery] attempt " + attempt + "/" + maxRetries + " failed: " + lastError);
            }
        }

        return new TranslationResult(false, null, maxRetries, lastError, attempts);
    }


    /**
     * One stateless chat completion using this instance's model and temperature:
     * the given system prompt and user message, and nothing else.
     * <p>
     * No translator state is involved — not {@link #SYSTEM_PROMPT}, not the retry
     * loop, not the message history of any {@code translate} call. A separate
     * instance created for another purpose therefore shares nothing with the one
     * doing translations beyond the class itself.
     *
     * @return the model's reply, verbatim
     */
    public String ask(String systemPrompt, String userMessage) throws IOException {
        return callGroq(List.of(msg("system", systemPrompt), msg("user", userMessage)));
    }

    // -------------------------------------------------------------------------
    // Groq API call
    // -------------------------------------------------------------------------

    private String callGroq(List<Map<String, String>> messages) throws IOException {
        boolean needsKey = requiresApiKey(endpoint);
        if (needsKey && (apiKey == null || apiKey.isBlank()))
            throw new IllegalStateException(
                    "GROQ_API_KEY not found. Add it to your .env file: GROQ_API_KEY=your_key_here");

        String body = buildJson(messages);
        HttpRequest.Builder b = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        // A self-hosted server usually wants no key; send one only if we have it
        // (vLLM's --api-key and friends accept the same header).
        if (apiKey != null && !apiKey.isBlank()) b.header("Authorization", "Bearer " + apiKey);

        try {
            HttpResponse<String> resp =
                    clientFor(endpoint).send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200)
                throw new RuntimeException(
                        "LLM API error " + resp.statusCode() + " from " + endpoint + ": " + resp.body());
            return parseContent(resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling " + endpoint, e);
        } catch (java.net.ConnectException e) {
            throw new IOException(
                    "Cannot reach the LLM endpoint " + endpoint +
                    (needsKey ? "" : " — is the local model server running?"), e);
        }
    }

    /**
     * Over cleartext the JDK client tries to upgrade to HTTP/2, sending
     * {@code Upgrade: h2c} plus {@code Connection: Upgrade}. uvicorn's h11 reads that
     * as a protocol switch and never passes the request body to the ASGI app, so a
     * self-hosted server (vLLM) rejects the call with "body: Field required". Pinning
     * cleartext connections to HTTP/1.1 drops those headers. TLS endpoints are left
     * alone: there h2 comes from ALPN, not this upgrade, so Groq keeps using HTTP/2.
     */
    private HttpClient clientFor(String endpoint) {
        if (!endpoint.regionMatches(true, 0, "http://", 0, 7)) return http;
        if (httpPlain == null)
            httpPlain = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
        return httpPlain;
    }

    /** Only the hosted provider needs a key; anything self-served is assumed open. */
    private static boolean requiresApiKey(String endpoint) {
        try {
            String host = URI.create(endpoint).getHost();
            return host != null && host.endsWith("groq.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // JSON helpers (no external dependency)
    // -------------------------------------------------------------------------

    private String buildJson(List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":").append(jsonStr(model))
          .append(",\"temperature\":").append(temperature).append(",\"messages\":[");
        for (int i = 0; i < messages.size(); i++) {
            Map<String, String> m = messages.get(i);
            sb.append("{\"role\":").append(jsonStr(m.get("role")))
              .append(",\"content\":").append(jsonStr(m.get("content"))).append("}");
            if (i < messages.size() - 1) sb.append(",");
        }
        sb.append("]}");
        return sb.toString();
    }

    /** Extracts choices[0].message.content from a Groq/OpenAI response JSON. */
    private static String parseContent(String json) {
        int idx = json.indexOf("\"choices\"");
        if (idx < 0) throw new RuntimeException("No 'choices' in Groq response: " + json);
        idx = json.indexOf("\"content\"", idx);
        if (idx < 0) throw new RuntimeException("No 'content' in Groq response: " + json);
        // Advance past  "content":  to the opening quote of the string value
        idx = json.indexOf('"', json.indexOf(':', idx) + 1) + 1;
        StringBuilder sb = new StringBuilder();
        while (idx < json.length()) {
            char c = json.charAt(idx++);
            if (c == '\\' && idx < json.length()) {
                char next = json.charAt(idx++);
                switch (next) {
                    case 'n'  -> sb.append('\n');
                    case 't'  -> sb.append('\t');
                    case 'r'  -> sb.append('\r');
                    case '"'  -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    default   -> { sb.append('\\'); sb.append(next); }
                }
            } else if (c == '"') {
                break;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Strips markdown fences and returns the first line containing SELECT(. */
    private static String extractDsl(String text) {
        text = text.replaceAll("```[a-zA-Z]*\\n?", "").trim();
        for (String line : text.split("\\n")) {
            int idx = line.indexOf("SELECT(");
            if (idx >= 0) return line.substring(idx).trim();
        }
        return text.trim();
    }

    private static Map<String, String> msg(String role, String content) {
        return Map.of("role", role, "content", content);
    }

    /** Encodes a Java string as a JSON string literal (including surrounding quotes). */
    private static String jsonStr(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"'  -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default   -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    // -------------------------------------------------------------------------
    // .env / API key resolution
    // -------------------------------------------------------------------------

    /** Environment first, then the nearest .env file; {@code null} if set in neither. */
    private static String resolveSetting(String name) {
        String fromEnv = System.getenv(name);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;

        Path envFile = findEnvFile();
        if (envFile != null) {
            String val = parseEnvFile(envFile).get(name);
            if (val != null && !val.isBlank()) return val;
        }
        return null;
    }

    /** LLM_ENDPOINT overrides the default Groq endpoint without touching code. */
    private static String resolveEndpoint() {
        String configured = resolveSetting("LLM_ENDPOINT");
        return configured != null ? configured.strip() : DEFAULT_ENDPOINT;
    }

    private static Path findEnvFile() {
        Path current = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 6; i++) {
            Path candidate = current.resolve(".env");
            if (Files.exists(candidate)) return candidate;
            current = current.getParent();
            if (current == null) break;
        }
        return null;
    }

    private static Map<String, String> parseEnvFile(Path path) {
        Map<String, String> map = new HashMap<>();
        try {
            for (String line : Files.readAllLines(path)) {
                line = line.strip();
                if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                int eq = line.indexOf('=');
                String key = line.substring(0, eq).strip();
                String val = line.substring(eq + 1).strip();
                if (val.length() >= 2 &&
                        ((val.startsWith("\"") && val.endsWith("\"")) ||
                         (val.startsWith("'")  && val.endsWith("'"))))
                    val = val.substring(1, val.length() - 1);
                map.put(key, val);
            }
        } catch (IOException e) {
            throw new RuntimeException("Cannot read .env: " + path, e);
        }
        return map;
    }
}