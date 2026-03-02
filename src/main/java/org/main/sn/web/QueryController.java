package org.main.sn.web;

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

    /**
     * Data root is configurable via:
     *   -Ddata.root=/path/to/gamestate-2024
     * or env var DATA_ROOT.
     * Falls back to $HOME/GeometricPatternMatching/data/SoccerNet/gamestate-2024
     */
    private static final Path GAMESTATE_ROOT = resolveDataRoot();

    private static Path resolveDataRoot() {
        String prop = System.getProperty("data.root");
        if (prop != null && !prop.isBlank()) return Paths.get(prop);
        String env = System.getenv("DATA_ROOT");
        if (env != null && !env.isBlank()) return Paths.get(env);
        return Paths.get(System.getProperty("user.home"),
                "GeometricPatternMatching", "data", "SoccerNet", "gamestate-2024");
    }

    private static final List<String> SPLITS = List.of("train", "test", "valid");

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
        Path videoPath = GAMESTATE_ROOT.resolve(split).resolve(instance).resolve("vid.mp4");
        Path absolute  = videoPath.toAbsolutePath();
        log.info("Video requested: " + absolute);

        if (!Files.exists(videoPath)) {
            log.warning("Video not found: " + absolute);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Not found: " + absolute);
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

    /** Debug endpoint: returns the resolved path for a video */
    @GetMapping("/video/{split}/{instance}/debug")
    public void debugVideo(@PathVariable("split") String split,
                           @PathVariable("instance") String instance,
                           HttpServletResponse response) throws IOException {
        Path videoPath = GAMESTATE_ROOT.resolve(split).resolve(instance).resolve("vid.mp4");
        Path absolute  = videoPath.toAbsolutePath();
        String msg = "exists=" + Files.exists(videoPath)
                + "\nresolved=" + absolute
                + "\nworkingDir=" + Paths.get("").toAbsolutePath()
                + "\nGAMESTATE_ROOT=" + GAMESTATE_ROOT;
        response.setContentType("text/plain");
        response.getWriter().write(msg);
    }

    /** Run a query expressed in DSL syntax and return results as JSON */
    @PostMapping("/query")
    public ResponseEntity<QueryResponse> runQuery(@RequestBody QueryRequest request) {
        try {
            SelectExpr expr = QueryEvaluator.evaluate(request.query());
            List<Result> results = expr.search();
            List<Map<String, Object>> jsonResults = toJson(results);
            String fullText = results.isEmpty() ? "" : String.join("\n", results.getFirst().formatAll(results));
            return ResponseEntity.ok(new QueryResponse(true, null, jsonResults, fullText));
        } catch (Exception e) {
            return ResponseEntity.ok(new QueryResponse(false, e.getMessage(), List.of(), ""));
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
    public record QueryResponse(boolean success, String error, List<Map<String, Object>> results, String fullText) {}
}

