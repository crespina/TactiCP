package org.main.sn.web;

import org.main.sn.dsl.SelectExpr;
import org.main.sn.logic.Result;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api")
public class QueryController {

    private static final Logger log = Logger.getLogger(QueryController.class.getName());

    private static final Path GAMESTATE_ROOT = Paths.get(
            System.getProperty("user.home"),
            "GeometricPatternMatching", "data", "SoccerNet", "gamestate-2024");
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

    /** Serve the video file directly via servlet response */
    @GetMapping("/video/{split}/{instance}")
    public void getVideo(@PathVariable("split") String split,
                         @PathVariable("instance") String instance,
                         HttpServletResponse response) throws IOException {
        Path videoPath = GAMESTATE_ROOT.resolve(split).resolve(instance).resolve("vid.mp4");
        Path absolute  = videoPath.toAbsolutePath();
        log.info("Video requested: " + absolute + " | cwd: " + Paths.get("").toAbsolutePath());

        if (!Files.exists(videoPath)) {
            String msg = "Video not found at: " + absolute + " | cwd: " + Paths.get("").toAbsolutePath();
            log.warning(msg);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, msg);
            return;
        }

        response.setContentType("video/mp4");
        response.setContentLengthLong(Files.size(videoPath));
        try (OutputStream out = response.getOutputStream()) {
            Files.copy(videoPath, out);
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
                + "\nworkingDir=" + Paths.get("").toAbsolutePath();
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

