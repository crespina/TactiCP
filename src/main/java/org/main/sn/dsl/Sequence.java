package org.main.sn.dsl;

import org.main.sn.logic.GameStateReconstructionInstance;
import org.main.sn.logic.Query;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class Sequence {
    public final String name;
    public final List<Event> steps = new ArrayList<>();
    public final List<GameStateReconstructionInstance> matches = new ArrayList<>();
    public int duration = -1;
    public int start, end, xcenter, ycenter, radius, xtop, ytop, w, h = -1;

    static final Path ROOT = Paths.get("data/SoccerNet/gamestate-2024");
    static final List<String> SPLITS = List.of("train", "test", "valid", "challenge");

    public Sequence(String name, Event... exprs) {
        if (name == null || name.isEmpty()) throw new IllegalArgumentException("name must not be null or empty");
        this.name = name;
        Collections.addAll(steps, exprs);
    }

    public Sequence start(int start) {
        if (start < 0) {
            throw new IllegalArgumentException("Start time must be a non-negative value.");
        }
        this.start = start;
        return this;
    }

    public Sequence end(int end) {
        if (end < 0) {
            throw new IllegalArgumentException("End time must be a non-negative value.");
        }
        this.end = end;
        return this;
    }

    public Sequence within(int duration) {
        if (duration < 0) {
            throw new IllegalArgumentException("Duration must be a non-negative value.");
        }
        this.duration = duration;
        return this;
    }

    public Sequence radius(int xcenter, int ycenter, int radius) {
        if (radius < 0) {
            throw new IllegalArgumentException("Radius must be a non-negative value.");
        }
        this.radius = radius;
        this.xcenter = xcenter;
        this.ycenter = ycenter;
        return this;
    }

    public Sequence rectangle (int xtop, int ytop, int w, int h){
        if (w < 0 || h < 0) {
            throw new IllegalArgumentException("Width and height must be non-negative values.");
        }
        this.xtop = xtop;
        this.ytop = ytop;
        this.w = w;
        this.h = h;
        return this;
    }

    public void from(String ids) throws IOException {
        Set<Path> selected = new HashSet<>();

        Set<String> tokens = Arrays.stream(ids.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        //"all" = all splits
        if (tokens.contains("all")) {
            tokens.addAll(SPLITS);
        }

        //handle split names
        for (String split : SPLITS) {
            if (tokens.contains(split)) {
                Path splitDir = ROOT.resolve(split);
                try (Stream<Path> s = Files.list(splitDir)) {
                    s.filter(Files::isDirectory)
                            .forEach(selected::add);
                }
            }
        }

        //handle explicit SNGS-xxx
        for (String token : tokens) {
            if (token.startsWith("SNGS-")) {
                selected.add(findSNGS(token));
            }
        }

        for (Path splitDir : selected) {
            matches.add(new GameStateReconstructionInstance(splitDir.toString()));
        }

    }

    private static Path findSNGS(String name) throws IOException {
        for (String split : SPLITS) {
            Path candidate = ROOT.resolve(split).resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown SNGS folder: " + name);
    }

    public void search() {
        Query query = new Query();
        query.apply(this);
    }


}
