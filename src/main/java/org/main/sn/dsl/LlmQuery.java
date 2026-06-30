package org.main.sn.dsl;

import org.main.sn.web.QueryEvaluator;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;

/**
 * Translates a natural-language query into a DSL query string using Groq's
 * LLaMA 3.3-70B, then validates the result by calling QueryEvaluator.evaluate()
 * directly — no running server required.
 *
 * Usage:
 *   LlmQuery llm = new LlmQuery();                   // reads GROQ_API_KEY from .env
 *   String dsl = llm.translate("find all passes", "SNGS-060");
 */
public class LlmQuery {

    private static final int    MAX_RETRIES = 3;
    private static final String GROQ_URL    = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL       = "llama-3.3-70b-versatile";

    private static final String SYSTEM_PROMPT = """
            You are an expert at translating natural language queries into a custom DSL for a \
            football (soccer) spatio-temporal pattern matching system.

            ## Entities

            ### Player
            - PLAYER("name")                        — anonymous wildcard player
            - PLAYER("name", jersey_num)            — player with jersey number (int)
            - PLAYER("name", "left")                — player from the left team
            - PLAYER("name", "right")               — player from the right team
            - PLAYER("name", "left", jersey_num)    — player from the left team with jersey number
            - PLAYER("name", "right", jersey_num)   — player from the right team with jersey number
              * The same identifier appearing twice refers to the same player performing both actions.

            ### Ball
            - BALL()

            ### Team
            - TEAM("LEFT")                              — all players of the left team in the match
            - TEAM("RIGHT")                             — all players of the right team in the match
            - TEAM("name", id1, id2, ...)               — team built from player jersey numbers
            - TEAM("name", PLAYER("p1"), PLAYER("p2"))  — team built from player entities

            ## Events

            ### Labeled events (span multiple frames; use .MINRANGE() to get minimal interval)
            - PLAYER("p").PASSTO(PLAYER("q"))              — p passes to q
            - PLAYER("p").MOVETO(from_zone, to_zone)       — player moves from zone to zone
            - BALL().MOVETO(from_zone, to_zone)            — ball moves from zone to zone
            - TEAM("t").MOVETO(from_zone, to_zone, k)      — at least k players of team move zone→zone

            ### Atomic events (hold at a single frame; usable with NOT)
            - POSSESSION(PLAYER("p"))                      — player has the ball
            - POSSESSION(TEAM("t"))                        — any player of the team has the ball
            - POSITION(PLAYER("p"), zone1, zone2, ...)     — player is in one of the listed zones
            - POSITION(BALL(), zone1, zone2, ...)          — ball is in one of the listed zones
            - POSITION(TEAM("t"), zone1, zone2, ...)       — at least 1 team player is in those zones
            - POSITION(TEAM("t"), K(k), zone1, zone2, ...) — at least k team players are in those zones

            ## Event modifier
            - event.MINRANGE()
              With MINRANGE: minimal contiguous interval where the predicate holds (e.g. ball-in-flight for a pass).
              Without MINRANGE: largest possession-based interval.

            ## Combinators
            - AND(event1, event2, ...)   — all events hold SIMULTANEOUSLY, i.e. their intervals overlap at the same time. AND says nothing about order: it is ONLY for co-occurring events, NEVER for events that happen one after another (see "Temporal semantics" below).
            - OR(event1, event2, ...)    — at least one event holds
            - NOT(event)                 — event does NOT hold (complement of intervals)
              NOT is only valid for atomic events: POSSESSION and POSITION. Do NOT use NOT with PASSTO, MOVETO, AND, or OR.

            ## Full query structure
            SELECT(event1, event2, ...).FROM("dataset").WHERE(constraint, ...).search_mode

            ## Temporal semantics — SEQUENCE vs SIMULTANEOUS (read carefully)
            How you combine events decides WHEN they happen relative to one another. This is a
            meaning difference, not a stylistic one — choosing the wrong form changes the query.

            - SEQUENCE (events happen one after another, in chronological order):
              list the events as separate, comma-separated arguments of SELECT, written in the
              order they occur in time — the first argument happens first, then the next, and so on.
                  SELECT(eventA, eventB)         — eventA happens, and AFTERWARDS eventB happens
              The intervals do NOT have to overlap; eventB is expected to occur after eventA.

            - SIMULTANEOUS (events happen at the same time):
              wrap the events in AND(...). Their intervals must overlap / intersect — they hold
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

            ### WHERE (optional)
            WHERE can be applied in two places:

            1. On a single event, to constrain only that event:
                 event.WHERE(constraint, ...)
               Example: PLAYER("p1").PASSTO(PLAYER("p2")).MINRANGE().WHERE(MAXDURATION(25))
               This restricts only that event's interval, not the whole sequence.

            2. On the full SELECT expression, to constrain the whole matched sequence:
                 SELECT(...).FROM("...").WHERE(constraint, ...)
               Example: SELECT(PLAYER("p1").MOVETO(3,7).MINRANGE()).FROM("all").WHERE(STARTMIN(100), ENDMAX(500))
               This restricts the overall start/end/duration/location of the entire match.

            ATLEAST and ATMOST are only valid on the full SELECT (not on individual events).

            Temporal constraints (time in frames, 1 frame = 1/25 s):
              STARTMIN(n)    — the match must start at or after frame n
              STARTMAX(n)    — the match must start at or before frame n
              ENDMIN(n)      — the match must end at or after frame n
              ENDMAX(n)      — the match must end at or before frame n
              MINDURATION(n) — the match must last at least n frames
              MAXDURATION(n) — the match must last at most n frames

            Spatial constraints (distances in metres):
              RECTANGLE(xtop, ytop, width, height) — the match must occur within this rectangle;
                (xtop, ytop) is the top-left corner, width along x-axis, height along y-axis
              RADIUS(cx, cy, r) — the match must occur within a circle centred at (cx, cy) with radius r

            Aggregation constraints (only on full SELECT):
              ATLEAST(n) — raise an error if fewer than n occurrences are found in a clip
              ATMOST(n)  — raise an error if more than n occurrences are found in a clip

            ### Search mode (optional, default = all results)
            - .COUNT()         — return only the number of matches
            - .SEARCH("all")   — return all matches (explicit default)
            - .SEARCH("first") — return the first match only
            - .SEARCH(k)       — return the first k matches

            ## Examples (natural language → DSL)

            Q: Find all passes from any player to any other player in game SNGS-060.
            A: SELECT(PLAYER("p1").PASSTO(PLAYER("p2")).MINRANGE()).FROM("SNGS-060")

            Q: Find every time the ball moves from zone 4 to zone 1 across all games, but only if there are at least 2 occurrences.
            A: SELECT(BALL().MOVETO(4, 1).MINRANGE()).FROM("all").WHERE(ATLEAST(2))

            Q: Find moments where player 11 passes to any player while player 3 does not have possession, in game SNGS-061.
            A: SELECT(AND(PLAYER("p11",11).PASSTO(PLAYER("p2")).MINRANGE(), NOT(POSSESSION(PLAYER("p3"))))).FROM("SNGS-061")

            Q: Find all moments where player 7 does not have the ball in game SNGS-060.
            A: SELECT(NOT(POSSESSION(PLAYER("p7",7)))).FROM("SNGS-060")

            Q: Find player 13 moving from zone 6 to zone 7 in game SNGS-061, but only between frame 10 and frame 740.
            A: SELECT(PLAYER("p13",13).MOVETO(6,7).MINRANGE()).FROM("SNGS-061").WHERE(STARTMIN(10),ENDMAX(740))

            Q: How many times does player 1 pass to player 2 in game SNGS-060?
            A: SELECT(PLAYER("p1").PASSTO(PLAYER("p2")).MINRANGE()).FROM("SNGS-060").COUNT()

            Q: Find all moments where player 1 is in zone 6 or zone 7 in game SNGS-061.
            A: SELECT(POSITION(PLAYER("p1"),6,7)).FROM("SNGS-061")

            Q: Find moments where player 13 moves from zone 6 to zone 7 at the same time as player 15 moves from zone 1 to zone 2, in game SNGS-061.
            A: SELECT(AND(PLAYER("p13",13).MOVETO(6,7).MINRANGE(), PLAYER("p15",15).MOVETO(1,2).MINRANGE())).FROM("SNGS-061")

            Q: Find the ball moving from zone 4 to zone 1, then from zone 14 to zone 9, in game SNGS-061.
            A: SELECT(BALL().MOVETO(4,1).MINRANGE(), BALL().MOVETO(14,9).MINRANGE()).FROM("SNGS-061")

            Q: Find all the times where player 11 passed to player 16 and then the ball moved from zone 3 to zone 4, in game SNGS-060.
            A: SELECT(PLAYER("p11",11).PASSTO(PLAYER("p16",16)).MINRANGE(), BALL().MOVETO(3, 4).MINRANGE()).FROM("SNGS-060")

            Q: Find all the times where player 11 passes to player 16 while the ball is moving from zone 3 to zone 4, in game SNGS-060.
            A: SELECT(AND(PLAYER("p11",11).PASSTO(PLAYER("p16",16)).MINRANGE(), BALL().MOVETO(3, 4).MINRANGE())).FROM("SNGS-060")

            Q: Find all moments where at least 3 players from team t1 are in zones 4, 5, or 6 across all games.
            A: SELECT(POSITION(TEAM("t1"), K(3), 4, 5, 6)).FROM("all")

            Q: Find the first instance where at least 4 players from the left team move from zone 3 to zone 7 across all games.
            A: SELECT(TEAM("LEFT").MOVETO(3, 7, 4).MINRANGE()).FROM("all").SEARCH("first")

            Q: Find moments where the right team has possession for at least 50 frames in game SNGS-060.
            A: SELECT(POSSESSION(TEAM("RIGHT"))).FROM("SNGS-060").WHERE(MINDURATION(50))

            Q: Find the first pass from a left-team player to a right-team player in the training split.
            A: SELECT(PLAYER("p1","left").PASSTO(PLAYER("p2","right")).MINRANGE()).FROM("train").SEARCH("first")

            Q: Find all moments where either player 7 has the ball or player 13 has the ball, in game SNGS-060.
            A: SELECT(OR(POSSESSION(PLAYER("p7",7)), POSSESSION(PLAYER("p13",13)))).FROM("SNGS-060")

            ## Output rules
            1. Output ONLY the DSL query on a single line — no markdown, no code fences, no explanation.
            2. The output MUST start with SELECT( and contain .FROM("...").
            3. Always append .MINRANGE() to PASSTO and MOVETO events.
            4. NEVER use NOT with PASSTO, MOVETO, AND, or OR.
            5. For events that happen one after another (chronological order: "then", "and then", "after", "followed by", ...), list them as comma-separated arguments of SELECT in time order. Use AND(...) ONLY for events that happen at the same time ("while", "at the same time", "during", ...). Do not confuse the two.
            """;

    private final String     apiKey;
    private final HttpClient http;

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

    /** Reads GROQ_API_KEY from the environment or from the nearest .env file. */
    public LlmQuery() {
        this.apiKey = resolveApiKey();
        this.http   = HttpClient.newHttpClient();
    }

    /** Use an explicit API key (useful for tests or programmatic setup). */
    public LlmQuery(String apiKey) {
        this.apiKey = apiKey;
        this.http   = HttpClient.newHttpClient();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

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
     * QueryEvaluator.evaluate(), and retries up to MAX_RETRIES times on failure.
     *
     * @param humanQuery natural-language description of the pattern
     * @param dataset    dataset to search ("all", "train", "SNGS-060", …)
     * @return a syntactically valid DSL query string
     * @throws IOException      if the Groq API call fails
     * @throws RuntimeException if the LLM cannot produce a valid query after MAX_RETRIES attempts
     */
    public String translate(String humanQuery, String dataset) throws IOException {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(msg("system", SYSTEM_PROMPT));
        messages.add(msg("user", "Dataset: \"" + dataset + "\"\nQuery: " + humanQuery));

        String lastError = null;

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
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
                return dsl;
            } catch (Exception e) {
                lastError = e.getMessage();
                System.out.println("[LlmQuery] attempt " + attempt + "/" + MAX_RETRIES + " failed: " + lastError);
            }
        }

        throw new RuntimeException(
                "LLM could not produce a valid DSL query after " + MAX_RETRIES + " attempts.\n" +
                "Last error: " + lastError);
    }

    // -------------------------------------------------------------------------
    // Groq API call
    // -------------------------------------------------------------------------

    private String callGroq(List<Map<String, String>> messages) throws IOException {
        String body = buildJson(messages);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200)
                throw new RuntimeException("Groq API error " + resp.statusCode() + ": " + resp.body());
            return parseContent(resp.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while calling Groq API", e);
        }
    }

    // -------------------------------------------------------------------------
    // JSON helpers (no external dependency)
    // -------------------------------------------------------------------------

    private static String buildJson(List<Map<String, String>> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":").append(jsonStr(MODEL))
          .append(",\"temperature\":0.1,\"messages\":[");
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

    private static String resolveApiKey() {
        String fromEnv = System.getenv("GROQ_API_KEY");
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv;

        Path envFile = findEnvFile();
        if (envFile != null) {
            String key = parseEnvFile(envFile).get("GROQ_API_KEY");
            if (key != null && !key.isBlank()) return key;
        }

        throw new IllegalStateException(
                "GROQ_API_KEY not found. Add it to your .env file: GROQ_API_KEY=your_key_here");
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