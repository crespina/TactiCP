package org.main.sn.dsl;

import org.main.sn.logic.GameStateReconstructionInstance;
import org.main.sn.logic.Query;
import org.main.sn.logic.Result;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    public int duringFrom = -1;
    public int duringTo = -1;
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

    /** The game folders named by FROM. They are loaded by {@link #games()}, on first use. */
    private final Set<Path> sources = new HashSet<>();
    /** The FROM argument, or null when the query has no FROM. */
    private String dataset;
    /** Whether the query ends with COUNT(): its answer is the number of matches. */
    private boolean count;

    static final Path ROOT = DataRoot.PATH;
    static final List<String> SPLITS = List.of("train", "test", "valid");

    /*
     * Building a query never loads a game and never runs the solver: every method called in a query
     * only records what it is given and checks it. The games are loaded, and the query is solved,
     * only by search(), searchAndPrint() and count(). A syntax check can therefore build the query
     * as it is written. Keep it that way: work on the data belongs in those three methods.
     */

    public SelectExpr(Event... exprs) {
        for (Event e : exprs) checkSupported(e);
        Collections.addAll(steps, exprs);
    }

    /**
     * Refuses a negated pass or move, which the compiler cannot model whatever the data. This is
     * checked here, not in NOT(), because NOT(NOT(e)) is e: only the final negation of an event
     * counts, and it is final once the event is given to SELECT.
     */
    private static void checkSupported(Event e) {
        if (e == null) throw new IllegalArgumentException("SELECT was given a null event.");
        if (e instanceof AndEvent and) {
            and.children().forEach(SelectExpr::checkSupported);
        } else if (e instanceof OrEvent or) {
            or.children().forEach(SelectExpr::checkSupported);
        } else if (e.isNegated && e.action() != null) {
            String word = switch (e.action().name) {
                case "PASS_TO" -> "PASSTO";
                case "BALL_MOVE_TO", "PLAYER_MOVE_TO", "TEAM_MOVE_TO" -> "MOVETO";
                default -> null;
            };
            if (word != null) {
                throw new IllegalArgumentException("NOT cannot apply to " + word
                        + ": only POSITION and POSSESSION can be negated.");
            }
        }
    }

    /** COUNT() comes last: nothing may be called on the query after it. */
    private void requireNotCounted(String call) {
        if (count) {
            throw new IllegalArgumentException(".COUNT() must be the last call of the query, but ."
                    + call + "(...) comes after it.");
        }
    }


    public SelectExpr WHERE(Where... parts) {
        requireNotCounted("WHERE");
        if (parts != null) for (Where p : parts) if (p != null) whereParts.add(p);
        applyWhereParts();
        return this;
    }

    /**
     * Shorthand for {@code .WHERE(ATLEAST(n))}. Writing the aggregation guard
     * directly on the SELECT is a natural spelling and costs nothing to accept.
     */
    public SelectExpr ATLEAST(int n) {
        return WHERE(Factory.ATLEAST(n));
    }

    /** Shorthand for {@code .WHERE(ATMOST(n))}. See {@link #ATLEAST(int)}. */
    public SelectExpr ATMOST(int n) {
        return WHERE(Factory.ATMOST(n));
    }

    public SelectExpr SEARCH(int mode){
        requireNotCounted("SEARCH");
        this.searchMode = mode;
        return this;
    }

    public SelectExpr SEARCH(String mode){
        requireNotCounted("SEARCH");
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
                case MAXDURATION -> this.maxDuration = w.values()[0];
                case MINDURATION -> this.minDuration = w.values()[0];
                case DURING -> {
                    this.duringFrom = w.values()[0];
                    this.duringTo = w.values()[1];
                }
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

    /**
     * Names the games to search: "all", a split ("train", "test", "valid"), single games
     * ("SNGS-060", "scalability-…"), or a comma-separated list of these. Each name is checked
     * against the dataset folders, but no game is loaded here: see {@link #games()}.
     *
     * @throws IllegalArgumentException for a game that has no folder in the dataset
     */
    public SelectExpr FROM(String ids) throws IOException {
        requireNotCounted("FROM");
        Set<Path> selected = sources;

        Set<String> tokens = Arrays.stream(ids.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());

        //"all" = all splits
        if (tokens.contains("all") | tokens.contains("ALL")) {
            tokens.addAll(SPLITS);
        }

        //handle split names
        for (String split : SPLITS) {
            if (tokens.contains(split)  | tokens.contains(split.toUpperCase())) {
                Path splitDir = ROOT.resolve(split.toLowerCase());
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

        dataset = dataset == null ? ids : dataset + "," + ids;
        return this;
    }

    /** The FROM argument as written, or null when the query has no FROM. */
    public String dataset() {
        return dataset;
    }

    /**
     * The games the query runs on: those named by FROM, loaded here the first time they are
     * needed, and any added to {@link #matches} directly.
     */
    public List<GameStateReconstructionInstance> games() {
        for (Path dir : sources) {
            matches.add(new GameStateReconstructionInstance(dir.toString()));
        }
        sources.clear();
        return matches;
    }

    /**
     * Enumerates the individual game identifiers that a {@code FROM} dataset
     * string expands to, <em>without</em> loading any game state. Each returned
     * token is itself a valid single-game {@code FROM} argument (e.g.
     * {@code "SNGS-060"}), so a caller can run a query one game at a time by
     * rewriting the dataset to each token in turn.
     *
     * <p>The token grammar mirrors {@link #FROM(String)}: {@code "all"} expands to
     * every game in the {@code train}/{@code test}/{@code valid} splits, a split
     * name expands to that split's games, and explicit {@code SNGS-…} /
     * {@code scalability-…} tokens are single games returned as-is. Results are
     * de-duplicated and sorted by id, so the iteration order — and hence which
     * game first reveals a difference between two queries — is deterministic.
     */
    public static List<String> gamesOf(String ids) throws IOException {
        Set<String> tokens = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(t -> !t.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // "all" = every game across all splits.
        if (tokens.remove("all") | tokens.remove("ALL")) {
            tokens.addAll(SPLITS);
        }

        Set<String> games = new TreeSet<>();   // sorted + de-duplicated
        for (String split : SPLITS) {
            if (tokens.contains(split) || tokens.contains(split.toUpperCase())) {
                Path splitDir = ROOT.resolve(split.toLowerCase());
                try (Stream<Path> s = Files.list(splitDir)) {
                    s.filter(Files::isDirectory)
                            .map(p -> p.getFileName().toString())
                            .forEach(games::add);
                }
            }
        }
        for (String token : tokens) {
            if (token.startsWith("SNGS-") || token.startsWith("scalability-")) {
                games.add(token);
            }
        }
        return new ArrayList<>(games);
    }

    /** A clip or instance name is one folder name: no separators, no "..". */
    private static void checkName(String name) {
        if (!name.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid clip name: " + name);
    }

    private static Path findSNGS(String name) {
        checkName(name);
        for (String split : SPLITS) {
            Path candidate = ROOT.resolve(split).resolve(name);
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("Unknown clip: " + name
                + " (no such folder in the train, test or valid split of the dataset; see DATA_ROOT)");
    }

    private static Path findScalability(String name) {
        checkName(name);
        Path candidate = ROOT.resolve("scalability").resolve(name.substring("scalability-".length()));
        if (Files.exists(candidate)) {
            return candidate;
        }

        throw new IllegalArgumentException("Unknown scalability instance: " + name);
    }

    public List<Result> search() {
        Query query = new Query();
        if (!games().isEmpty()) {
            return query.apply(this);
        } else {
            throw new IllegalStateException("No matches specified for sequence");
        }
    }

    public void searchAndPrint() {
        Query query = new Query();
        if (!games().isEmpty()) {
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

    /**
     * Asks for the number of matches instead of the matches. It only records the request:
     * {@link #count()} runs the query. It must be the last call of the query.
     */
    public SelectExpr COUNT() {
        requireNotCounted("COUNT");
        count = true;
        return this;
    }

    /** Whether the query ends with COUNT(). */
    public boolean isCount() {
        return count;
    }

    /** Runs the query and returns its number of matches. */
    public int count() {
        return search().size();
    }
}



