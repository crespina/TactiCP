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
    public int start, end = -1;
    static final Path ROOT = Paths.get("data/SoccerNet/gamestate-2024");
    static final List<String> SPLITS = List.of("train", "test", "valid", "challenge");

    public Sequence(String name, Event... exprs) {
        this.name = name;
        Collections.addAll(steps, exprs);
    }

    public Sequence start(int start) {
        this.start = start;
        return this;
    }

    public Sequence end(int end) {
        this.end = end;
        return this;
    }

    public Sequence within(int duration) {
        this.duration = duration;
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
