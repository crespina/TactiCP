package org.main.sn.web;

import org.main.sn.dsl.DataRoot;
import org.main.sn.dsl.LlmQuery;
import org.main.sn.dsl.SelectExpr;
import org.main.sn.logic.Result;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
public class QueryController {

    private static final Logger log = Logger.getLogger(QueryController.class.getName());

    /** The dataset folder, see {@link DataRoot}. */
    private static final Path GAMESTATE_ROOT = DataRoot.PATH;

    private static final List<String> SPLITS = List.of("train", "test", "valid");

    /** A clip folder name such as {@code SNGS-060}; anything else is refused before touching the disk. */
    private static final java.util.regex.Pattern INSTANCE_NAME = java.util.regex.Pattern.compile("[A-Za-z0-9_-]+");

    /**
     * Lazily-built natural-language → DSL translator. Built on first use so that a
     * missing GROQ_API_KEY only breaks the /translate endpoint, not the whole app.
     */
    private volatile LlmQuery llmQuery;

    private LlmQuery llm() {
        LlmQuery local = llmQuery;
        if (local == null) {
            synchronized (this) {
                local = llmQuery;
                if (local == null) {
                    local = new LlmQuery();   // throws IllegalStateException if no API key
                    llmQuery = local;
                }
            }
        }
        return local;
    }

    /** List all available instances that have a video file */
    @GetMapping("/instances")
    public List<Map<String, String>> listInstances() throws IOException {
        List<Map<String, String>> result = new ArrayList<>();
        for (String split : SPLITS) {
            Path splitDir = GAMESTATE_ROOT.resolve(split);
            if (!Files.isDirectory(splitDir)) continue;
            try (Stream<Path> dirs = Files.list(splitDir)) {
                dirs.filter(Files::isDirectory)
                    .sorted()
                    .forEach(dir -> {
                        String name = dir.getFileName().toString();
                        Map<String, String> entry = new LinkedHashMap<>();
                        entry.put("id", name);
                        entry.put("split", split);
                        entry.put("label", split + "/" + name);
                        entry.put("hasVideo", String.valueOf(Files.exists(dir.resolve("vid.mp4"))));
                        result.add(entry);
                    });
            }
        }
        return result;
    }

    /**
     * Serve video with full HTTP Range support.
     * Browsers require Range requests for video seeking / streaming.
     */
    @GetMapping("/video/{split}/{instance}")
    public void getVideo(@PathVariable("split") String split,
                         @PathVariable("instance") String instance,
                         HttpServletRequest request,
                         HttpServletResponse response) throws IOException {
        if (!SPLITS.contains(split) || !INSTANCE_NAME.matcher(instance).matches()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Path videoPath = GAMESTATE_ROOT.resolve(split).resolve(instance).resolve("vid.mp4");

        if (!Files.exists(videoPath)) {
            log.warning("Video not found: " + videoPath.toAbsolutePath());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No video for " + split + "/" + instance);
            return;
        }

        long fileLength = Files.size(videoPath);
        String rangeHeader = request.getHeader("Range");

        response.setContentType("video/mp4");
        response.setHeader("Accept-Ranges", "bytes");

        if (rangeHeader == null) {
            // No Range header: send the whole file
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentLengthLong(fileLength);
            try (OutputStream out = response.getOutputStream()) {
                Files.copy(videoPath, out);
            }
        } else {
            // Parse Range: bytes=start-end
            long start = 0;
            long end = fileLength - 1;
            String rangeValue = rangeHeader.replace("bytes=", "").trim();
            String[] parts = rangeValue.split("-");
            if (!parts[0].isEmpty()) {
                start = Long.parseLong(parts[0]);
            }
            if (parts.length > 1 && !parts[1].isEmpty()) {
                end = Long.parseLong(parts[1]);
            }
            if (start > end || start >= fileLength) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileLength);
                return;
            }
            if (end >= fileLength) {
                end = fileLength - 1;
            }
            long contentLength = end - start + 1;

            response.setStatus(206); // Partial Content
            response.setContentLengthLong(contentLength);
            response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);

            try (RandomAccessFile raf = new RandomAccessFile(videoPath.toFile(), "r");
                 OutputStream out = response.getOutputStream()) {
                raf.seek(start);
                byte[] buffer = new byte[8192];
                long remaining = contentLength;
                while (remaining > 0) {
                    int toRead = (int) Math.min(buffer.length, remaining);
                    int read = raf.read(buffer, 0, toRead);
                    if (read <= 0) break;
                    out.write(buffer, 0, read);
                    remaining -= read;
                }
            }
        }
    }

    /** Run a query expressed in DSL syntax and return results as JSON */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> runQuery(@RequestBody QueryRequest request) {
        try {
            Object evaluated = QueryEvaluator.evaluate(request.query());

            if (evaluated instanceof Integer count) {
                // COUNT() query – return the count without full results
                return ResponseEntity.ok(new QueryResponse(true, null, List.of(), "Count: " + count, count));
            }

            SelectExpr expr = (SelectExpr) evaluated;
            List<Result> results = expr.search();
            List<Map<String, Object>> jsonResults = toJson(results);
            String fullText = results.isEmpty() ? "" : String.join("\n", results.getFirst().formatAll(results));
            return ResponseEntity.ok(new QueryResponse(true, null, jsonResults, fullText, null));
        } catch (Exception e) {
            return ResponseEntity.ok(new QueryResponse(false, e.getMessage(), List.of(), "", null));
        }
    }

    /**
     * Translate a natural-language description into a DSL query string using the
     * LLM. The dataset is supplied by the caller (the web UI collects it up front),
     * so the interactive "which dataset?" prompt used by the CLI is never reached.
     */
    @PostMapping("/translate")
    public ResponseEntity<TranslateResponse> translateQuery(@RequestBody TranslateRequest request) {
        String description = request.description() == null ? "" : request.description().trim();
        if (description.isEmpty()) {
            return ResponseEntity.ok(new TranslateResponse(false, null,
                    "Please describe the query you want in plain English."));
        }
        String dataset = (request.dataset() == null || request.dataset().isBlank())
                ? "all"
                : request.dataset().trim();
        try {
            String dsl = llm().translate(description, dataset);
            return ResponseEntity.ok(new TranslateResponse(true, dsl, null));
        } catch (IllegalStateException e) {
            // GROQ_API_KEY missing / not resolvable
            return ResponseEntity.ok(new TranslateResponse(false, null, e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.ok(new TranslateResponse(false, null,
                    "Translation failed: " + e.getMessage()));
        }
    }

    // ---------------------------------------------------------------
    // JSON serialisation helpers
    // ---------------------------------------------------------------

    private List<Map<String, Object>> toJson(List<Result> results) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Result r : results) {
            Map<String, Object> sol = new LinkedHashMap<>();
            sol.put("solutionIndex", r.solutionIndex);
            sol.put("instance", r.instance);
            sol.put("text", String.join("\n", r.toFormattedStrings()));
            sol.put("events", r.events.stream().map(this::eventToJson).collect(Collectors.toList()));
            // top-level interval = union of all events
            int minStart = r.events.stream().mapToInt(e -> e.interval.start).min().orElse(-1);
            int maxEnd   = r.events.stream().mapToInt(e -> e.interval.end).max().orElse(-1);
            sol.put("intervalStart", minStart);
            sol.put("intervalEnd", maxEnd);
            out.add(sol);
        }
        return out;
    }

    private Map<String, Object> eventToJson(Result.ResultEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", e.type);
        m.put("eventIdx", e.eventIdx);
        m.put("negated", e.negated);
        if (e.interval != null) {
            m.put("start", e.interval.start);
            m.put("end", e.interval.end);
            m.put("length", e.interval.length);
        }
        if (e instanceof Result.PassEvent pe) {
            m.put("passerId", pe.passerId);
            m.put("receiverId", pe.receiverId);
        } else if (e instanceof Result.PossessionEvent ps) {
            m.put("playerIds", ps.playerIds);
        } else if (e instanceof Result.PlayerMoveEvent mv) {
            m.put("playerId", mv.playerId);
        } else if (e instanceof Result.TeamMoveEvent tm) {
            m.put("playerIds", tm.playerIds);
        } else if (e instanceof Result.PositionEvent pos) {
            m.put("playerIds", pos.playerIds);
        } else if (e instanceof Result.GroupEvent ge) {
            m.put("groupKind", ge.groupKind);
            m.put("children", ge.children.stream().map(this::eventToJson).collect(Collectors.toList()));
        }
        return m;
    }

    // DTO records
    public record QueryRequest(String query) {}
    public record QueryResponse(boolean success, String error, List<Map<String, Object>> results, String fullText, Integer count) {}
    public record TranslateRequest(String description, String dataset) {}
    public record TranslateResponse(boolean success, String query, String error) {}
}

