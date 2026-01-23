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
    public int start = -1;
    public int end = -1;
    public int xcenter = -1;
    public int ycenter = -1;
    public int radius = -1;
    public int xtop = -1;
    public int ytop = -1;
    public int w = -1;
    public int h = -1;

    static final Path ROOT = Paths.get("data/SoccerNet/gamestate-2024");
    static final List<String> SPLITS = List.of("train", "test", "valid");

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

    public Sequence rectangle(int xtop, int ytop, int w, int h) {
        if (w < 0 || h < 0) {
            throw new IllegalArgumentException("Width and height must be non-negative values.");
        }
        this.xtop = xtop;
        this.ytop = ytop;
        this.w = w;
        this.h = h;
        return this;
    }

    public Sequence from(String ids) throws IOException {
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

    private static final int MAX_EXPANSIONS = 20;

    /**
     * Expands ORs into concrete sequences
     */
    public List<Sequence> expand() {
        List<Sequence> out = new ArrayList<>();
        expandRec(0, new ArrayList<>(), out);
        if (out.size() > MAX_EXPANSIONS) {
            throw new IllegalStateException("Too many OR combinations: " + out.size());
        }
        return out;
    }

    private void expandRec(int idx, List<Event> cur, List<Sequence> out) {
        if (idx == steps.size()) {
            Sequence s = new Sequence(name, cur.toArray(new Event[0]));
            copyMetaTo(s);
            out.add(s);
            return;
        }

        Event e = steps.get(idx);

        if (e instanceof OrEvent or) {
            for (Event alt : or.alternatives()) {
                cur.add(alt);
                expandRec(idx + 1, cur, out);
                cur.removeLast();
            }
        } else {
            cur.add(e);
            expandRec(idx + 1, cur, out);
            cur.removeLast();
        }
    }

    private void copyMetaTo(Sequence s) {
        s.duration = this.duration;
        s.start = this.start;
        s.end = this.end;
        s.xcenter = this.xcenter;
        s.ycenter = this.ycenter;
        s.radius = this.radius;
        s.xtop = this.xtop;
        s.ytop = this.ytop;
        s.w = this.w;
        s.h = this.h;
        s.matches.addAll(this.matches);
    }


    public void search() {
        if (matches.isEmpty()) {
            throw new IllegalStateException("No matches specified for sequence: " + name);
        }
        Query query = new Query();
        for (Sequence s : expand()) {
            List<String> toPrint = query.apply(s);
            for (String str : toPrint) {
                System.out.println(str);
            }
        }
    }

    public void count() {
        if (matches.isEmpty()) {
            throw new IllegalStateException("No matches specified for sequence: " + name);
        }
        Query query = new Query();
        int count = 0;
        for (Sequence seq : expand()) {
            List<String> toPrint = query.apply(seq);
            for (String s : toPrint) {
                System.out.println(s);
                if (s.equals("\n")) {
                    count++;
                }
            }
        }
        System.out.println("\n=========================");
        System.out.println("Total number of solutions is: " + count);
    }

    public void atMost(int maxCount) {
        if (matches.isEmpty()) {
            throw new IllegalStateException("No matches specified for sequence: " + name);
        }
        Query query = new Query();
        int count = 0;
        for (Sequence seq : expand()) {
            List<String> toPrint = query.apply(seq);
            for (String s : toPrint) {
                System.out.println(s);
                if (s.equals("\n")) {
                    count++;
                }
            }
        }
        System.out.println("\n=========================");
        System.out.println("Total number of solutions is: " + count);
        if (count > maxCount) {
            System.out.println("\n=========================");
            System.out.println("Reached the maximum number of solutions: " + maxCount);
        }
    }

    public void atLeast(int minCount) {
        if (matches.isEmpty()) {
            throw new IllegalStateException("No matches specified for sequence: " + name);
        }
        Query query = new Query();
        int count = 0;
        for (Sequence seq : expand()) {
            List<String> toPrint = query.apply(seq);
            for (String s : toPrint) {
                System.out.println(s);
                if (s.equals("\n")) {
                    count++;
                }
            }
        }
        System.out.println("\n=========================");
        System.out.println("Total number of solutions is: " + count);
        if (count < minCount) {
            System.out.println("\n=========================");
            System.out.println("Did not reach the minimum number of solutions: " + minCount);
        }
    }
}



