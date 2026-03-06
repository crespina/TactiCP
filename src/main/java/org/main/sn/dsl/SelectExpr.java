package org.main.sn.dsl;

import org.main.sn.logic.GameStateReconstructionInstance;
import org.main.sn.logic.Query;
import org.main.sn.logic.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class SelectExpr {
    public final List<Event> steps = new ArrayList<>();
    public final List<GameStateReconstructionInstance> matches = new ArrayList<>();
    public int maxDuration = -1;
    public int minDuration = -1;
    public int startMin = -1;
    public int startMax = -1;
    public int endMin = -1;
    public int endMax = -1;
    public int xcenter = -1;
    public int ycenter = -1;
    public int radius = -1;
    public int xtop = -1;
    public int ytop = -1;
    public int w = -1;
    public int h = -1;
    private final List<Where> whereParts = new ArrayList<>();
    public int atMost = -1;
    public int atLeast = -1;
    public int searchMode = -1; // 0: all, 1: first, other: count

    static final Path ROOT = Paths.get(
            System.getProperty("user.home"),
            "GeometricPatternMatching", "data", "SoccerNet", "gamestate-2024");
    static final List<String> SPLITS = List.of("train", "test", "valid");

    public SelectExpr(Event... exprs) {
        Collections.addAll(steps, exprs);
    }


    public SelectExpr WHERE(Where... parts) {
        if (parts != null) for (Where p : parts) if (p != null) whereParts.add(p);
        applyWhereParts();
        return this;
    }

    public SelectExpr SEARCH(int mode){
        this.searchMode = mode;
        return this;
    }

    public SelectExpr SEARCH(String mode){
        if (mode.equalsIgnoreCase("all")) {
            this.searchMode = 0;
        } else if (mode.equalsIgnoreCase("first")) {
            this.searchMode = 1;
        } else {
            throw new IllegalArgumentException("Invalid search mode: " + mode);
        }
        return this;
    }


    private void applyWhereParts() {
        for (Where w : whereParts) {
            switch (w.kind()) {
                case STARTMIN -> this.startMin = w.values()[0];
                case STARTMAX -> this.startMax = w.values()[0];
                case ENDMIN -> this.endMin = w.values()[0];
                case ENDMAX -> this.endMax = w.values()[0];
                case MAX_DURATION -> this.maxDuration = w.values()[0];
                case MIN_DURATION -> this.minDuration = w.values()[0];
                case RADIUS -> {
                    this.xcenter = w.values()[0];
                    this.ycenter = w.values()[1];
                    this.radius = w.values()[2];
                }
                case RECTANGLE -> {
                    this.xtop = w.values()[0];
                    this.ytop = w.values()[1];
                    this.w = w.values()[2];
                    this.h = w.values()[3];
                }
                case ATMOST -> this.atMost = w.values()[0];
                case ATLEAST -> this.atLeast = w.values()[0];
            }
        }
    }

    public SelectExpr FROM(String ids) throws IOException {
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
            } else if (token.startsWith("scalability-")) {
                selected.add(findScalability(token));
            }
        }

        for (Path splitDir : selected) {
            matches.add(new GameStateReconstructionInstance(splitDir.toString()));
        }

        return this;

    }

    private static Path findSNGS(String name) {
        for (String split : SPLITS) {
            Path candidate = ROOT.resolve(split).resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown SNGS folder: " + name);
    }

    private static Path findScalability(String name) {
        Path candidate = ROOT.resolve("scalability").resolve(name.substring("scalability-".length()));
        if (Files.exists(candidate)) {
            return candidate;
        }

        throw new IllegalArgumentException("Unknown SNGS folder: " + name);
    }

    public List<Result> search() {
        Query query = new Query();
        if (!matches.isEmpty()) {
            return query.apply(this);
        } else {
            throw new IllegalStateException("No matches specified for sequence");
        }
    }

    public void searchAndPrint() {
        Query query = new Query();
        if (!matches.isEmpty()) {
            List<Result> r = query.apply(this);
            if (r.size() == 0) {
                System.out.println("No matches found for the given query.");
                return;
            } else {
                List<String> out = r.getFirst().formatAll(r);
                out.forEach(System.out::println);
            }

        } else {
            throw new IllegalStateException("No matches specified for sequence");
        }
    }

    public int COUNT(){
        Query query = new Query();
        if (!matches.isEmpty()) {
            List<Result> results = query.apply(this);
            return results.size();
        } else {
            throw new IllegalStateException("No matches specified for sequence");
        }
    }
}



