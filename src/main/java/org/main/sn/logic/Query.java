package org.main.sn.logic;

import org.main.sn.dsl.*;
import org.main.util.Automaton;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;


import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.maxicp.cp.CPFactory.*;

public class Query {

    private static final int ABSENT = 1;

    public static final String MAX_SOLUTIONS_PROPERTY = "sn.maxSolutions";
    private static final int DEFAULT_MAX_SOLUTIONS = 5_000_000;

    public static int maxSolutions() {
        return Integer.getInteger(MAX_SOLUTIONS_PROPERTY, DEFAULT_MAX_SOLUTIONS);
    }

    /** Optional wall-clock budget per instance search, for benchmarking; 0 or less means none. */
    public static final String TIME_LIMIT_PROPERTY = "sn.timeLimitMs";

    private final Map<CPIntervalVar, CPIntVar> startVars = new IdentityHashMap<>();
    private final Map<CPIntervalVar, CPIntVar> endVars = new IdentityHashMap<>();

    private final Set<CPIntervalVar> mayBeAbsent = Collections.newSetFromMap(new IdentityHashMap<>());

    /** The optional windows of the candidates of an event (see chooseCandidate), absent unless chosen. */
    private final Set<CPIntervalVar> candidateWindows = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Variables the search branches on besides the identities and the event intervals. */
    private final List<CPIntVar> decisionVars = new ArrayList<>();

    /** How many times the query names each identity key, to tell the wildcards named only once. */
    private final Map<String, Integer> identityUses = new HashMap<>();

    /** A player a team event picks without an identity of its own, and the event's interval. */
    private record AnonymousPlayer(CPIntVar player, CPIntervalVar owner) {}

    /** The anonymous members of team events: each is still another player than every named one. */
    private final List<AnonymousPlayer> anonymousPlayers = new ArrayList<>();

    /** Solver effort of the last {@link #apply} call, summed over its instances. */
    public record Effort(long nodes, long failures, long modelNanos, long searchNanos, int timeouts) {}

    private Effort effort = new Effort(0, 0, 0, 0, 0);

    public Effort effort() {
        return effort;
    }

    public List<Result> apply(SelectExpr seq) {


        List<Result> r = new ArrayList<>();
        long nodes = 0, failures = 0, modelNanos = 0, searchNanos = 0;
        int timeouts = 0;

        final List<Event> steps = seq.steps;
        final List<GameStateReconstructionInstance> matches = seq.games();
        int total_max_duration = seq.maxDuration;
        int total_min_duration = seq.minDuration;
        int total_start_min = seq.startMin;
        int total_start_max = seq.startMax;
        int total_end_min = seq.endMin;
        int total_end_max = seq.endMax;
        int total_during_from = seq.duringFrom;
        int total_during_to = seq.duringTo;
        int total_radius = seq.radius;
        int total_xcenter = seq.xcenter;
        int total_ycenter = seq.ycenter;
        int total_xtop = seq.xtop;
        int total_ytop = seq.ytop;
        int total_w = seq.w;
        int total_h = seq.h;
        boolean total_circle = (total_radius != -1);
        boolean total_rectangle = (total_w != -1 && total_h != -1);
        int searchMode = seq.searchMode; // 0: all, 1: first, other: count
        int atMost = seq.atMost;
        int atLeast = seq.atLeast;

        for (GameStateReconstructionInstance instance : matches) {

            long modelStart = System.nanoTime();
            CPSolver cp = makeSolver();

            Map<String, CPIntVar> IDENTIFIERS = new HashMap<>(); //Entity's name -> CP variable

            ArrayList<ExtendedCPVar> extVars = new ArrayList<>();
            ArrayList<CPIntervalVar> intervals = new ArrayList<>();
            ArrayList<CPIntervalVar> topIntervals = new ArrayList<>();
            AtomicInteger counterVars = new AtomicInteger(-1);
            AtomicInteger counterEvent = new AtomicInteger(0);

            startVars.clear();
            endVars.clear();
            identityUsers.clear();
            mayBeAbsent.clear();
            candidateWindows.clear();
            alwaysPresentIdentities.clear();
            decisionVars.clear();
            identityUses.clear();
            anonymousPlayers.clear();
            for (Event event : steps) countIdentityUses(event, identityUses);

            try {
                int[] teams = instance.teams;
                int n = instance.n;
                int ball_idx = instance.ball_idx;
                int nbZones = instance.nbZones;
                Possession p = new Possession(cp, instance, true);
                // a player keeps the ball between two touches as long as nobody else touches it
                int[] possession = bridgeSamePlayer(p.result);
                Position pos = new Position(cp, instance);
                int[][] positionZones = pos.position;

                int[][] positionBox_x = new int[teams.length][n];
                int[][] positionBox_y = new int[teams.length][n];

                Map<Integer, GameStateReconstructionInstance.FrameData> frameData = instance.positions;

                for (int f = 0; f < n; f++) {
                    GameStateReconstructionInstance.FrameData framedata = frameData.get(f + 1);
                    Map<Integer, GameStateReconstructionInstance.PlayerInfo> fd = framedata.players; //(player id, player info)

                    for (Map.Entry<Integer, GameStateReconstructionInstance.PlayerInfo> entry : fd.entrySet()) {
                        Integer playerId = entry.getKey();
                        GameStateReconstructionInstance.PlayerInfo playerInfo = entry.getValue();
                        GameStateReconstructionInstance.Position playerPos = playerInfo.pos();
                        int x = (int) playerPos.x();
                        int y = (int) playerPos.y();
                        positionBox_x[playerId][f] = x;
                        positionBox_y[playerId][f] = y;
                    }

                }

                CPIntervalVar prevTopInterval = null;
                for (Event event : steps) {
                    // every step happens; the intervals of the members of a group are made with it
                    CPIntervalVar event_interval = makeIntervalVar(cp, false, 0, n + 1);
                    intervals.add(event_interval);
                    topIntervals.add(event_interval);
                    CPIntVar event_start = startOf(event_interval);
                    CPIntVar event_end = endOf(event_interval);
                    cp.post(gt(event_end, event_start)); // a step spans at least one frame (end is exclusive)
                    // the steps follow one another, whatever their kind: a step starts at the earliest
                    // on the last frame of the previous one (for an AND, its intersection; for an OR,
                    // its chosen branch)
                    if (prevTopInterval != null) {
                        cp.post(endBeforeStart(prevTopInterval, event_interval, 1));
                    }

                    modelEvent(event, List.of(), cp, counterEvent, event_interval, ball_idx, n, positionZones, positionBox_x,
                            positionBox_y, total_circle, extVars, intervals, total_rectangle, counterVars, teams,
                            IDENTIFIERS, total_xcenter, possession, total_ycenter, total_radius, total_xtop, total_ytop,
                            total_w, total_h, nbZones, total_during_from, total_during_to);
                    prevTopInterval = event_interval;
                }

                // Distinct names are distinct players. An identity stands for a player whenever one
                // of the events naming it happens; the global allDifferent covers the identities
                // that always do, the others are distinct from them only while they happen.
                List<CPIntVar> alwaysPresent = new ArrayList<>();
                List<String> branchLocal = new ArrayList<>();
                Map<String, CPBoolVar> presence = new HashMap<>();
                for (Map.Entry<String, CPIntVar> identity : IDENTIFIERS.entrySet()) {
                    CPBoolVar present = identityPresence(identity.getKey());
                    if (present == null) {
                        alwaysPresent.add(identity.getValue());
                    } else {
                        branchLocal.add(identity.getKey());
                        presence.put(identity.getKey(), present);
                    }
                }

                if (!alwaysPresent.isEmpty()) cp.post(allDifferent(alwaysPresent.toArray(CPIntVar[]::new)));

                for (int a = 0; a < branchLocal.size(); a++) {
                    CPIntVar va = IDENTIFIERS.get(branchLocal.get(a));
                    CPBoolVar pa = presence.get(branchLocal.get(a));
                    for (CPIntVar vb : alwaysPresent) {
                        postDistinctWhenBothPresent(cp, pa, null, va, vb);
                    }
                    for (int b = a + 1; b < branchLocal.size(); b++) {
                        postDistinctWhenBothPresent(cp, pa, presence.get(branchLocal.get(b)),
                                va, IDENTIFIERS.get(branchLocal.get(b)));
                    }
                }

                // an identity that stands for nobody takes one value, so that it adds no answer
                for (String name : branchLocal) {
                    CPIntVar identity = IDENTIFIERS.get(name);
                    cp.post(implies(not(presence.get(name)), isEq(identity, identity.min())));
                }

                // an anonymous team member is another player than every named one, and than the
                // anonymous members of the other team events
                for (int a = 0; a < anonymousPlayers.size(); a++) {
                    AnonymousPlayer anonymous = anonymousPlayers.get(a);
                    CPBoolVar pa = presenceOf(anonymous.owner());
                    for (Map.Entry<String, CPIntVar> identity : IDENTIFIERS.entrySet()) {
                        postDistinctWhenBothPresent(cp, pa, presence.get(identity.getKey()), anonymous.player(), identity.getValue());
                    }
                    for (int b = a + 1; b < anonymousPlayers.size(); b++) {
                        AnonymousPlayer other = anonymousPlayers.get(b);
                        if (other.owner() == anonymous.owner()) continue;
                        postDistinctWhenBothPresent(cp, pa, presenceOf(other.owner()), anonymous.player(), other.player());
                    }
                }

                //total duration
                CPIntervalVar lastVar = topIntervals.getLast();
                CPIntervalVar firstVar = topIntervals.getFirst();

                if (total_max_duration != -1) {
                    cp.post(le(sum(endOf(lastVar), minus(startOf(firstVar))), total_max_duration));
                }
                if (total_min_duration != -1) {
                    cp.post(ge(sum(endOf(lastVar), minus(startOf(firstVar))), total_min_duration));
                }
                if (total_start_min != -1) {
                    cp.post(ge(startOf(firstVar), total_start_min));
                }
                if (total_start_max != -1) {
                    cp.post(le(startOf(firstVar), total_start_max));
                }
                if (total_end_min != -1) {
                    cp.post(gt(endOf(lastVar), total_end_min)); //end is exclusive so > k means "last frame >= k"
                }
                if (total_end_max != -1) {
                    cp.post(le(endOf(lastVar), total_end_max + 1));
                }
            } catch (InconsistencyException e) {
                //System.out.println("No solution found for instance " + instance.name);
                modelNanos += System.nanoTime() - modelStart;
                continue;
            }

            // ******
            // SEARCH
            // ******

            long searchStart = System.nanoTime();
            modelNanos += searchStart - modelStart;
            DFSearch search = makeDfs(cp, Searches.firstFailBinary(
                    Stream.of(
                            IDENTIFIERS.values().stream(),
                            decisionVars.stream(),
                            intervals.stream().flatMap(itv -> mayBeAbsent.contains(itv)
                                    ? Stream.<CPIntVar>of(itv.status(), startOf(itv), endOf(itv))
                                    : Stream.<CPIntVar>of(startOf(itv), endOf(itv)))
                    ).flatMap(s -> s).toArray(CPIntVar[]::new)));
            AtomicInteger solutionCounter = new AtomicInteger(0);

            search.onSolution(() -> {
                int solnb = solutionCounter.getAndIncrement();
                r.add(parseSolution(solnb, extVars, instance.name));
            });

            // Stop predicate for the interval-count guard: solve(...) halts as soon as
            // this returns true. A cap of 0 or less means "no guard".
            final int solutionCap = maxSolutions();
            Predicate<SearchStatistics> solutionGuard = solutionCap > 0
                    ? statistics -> statistics.numberOfSolutions() >= solutionCap
                    : statistics -> false;
            // an instance that runs out of the time budget keeps the matches found so far
            final long timeLimitMs = Long.getLong(TIME_LIMIT_PROPERTY, 0);
            final long deadline = System.nanoTime() + timeLimitMs * 1_000_000;
            Predicate<SearchStatistics> capReached = timeLimitMs > 0
                    ? solutionGuard.or(statistics -> System.nanoTime() > deadline)
                    : solutionGuard;

            // SEARCH(k) caps how many answers a game returns (all for -1 and 0); ATLEAST(n) and
            // ATMOST(n) bound how many it has. So the search goes on until the bounds can be
            // decided: n answers for ATLEAST(n), n + 1 for ATMOST(n), past which the game is dropped
            // anyway.
            final int returned = searchMode >= 1 ? searchMode : Integer.MAX_VALUE;
            final int stopAt = atMost >= 0 ? atMost + 1 : Math.max(returned, atLeast);
            SearchStatistics stats = stopAt == Integer.MAX_VALUE
                    ? search.solve(capReached)
                    : search.solve(statistics -> statistics.numberOfSolutions() >= stopAt || capReached.test(statistics));
            searchNanos += System.nanoTime() - searchStart;
            if (timeLimitMs > 0 && System.nanoTime() > deadline) timeouts++;
            if (stats != null) {
                nodes += stats.numberOfNodes();
                failures += stats.numberOfFailures();
            }

            if (solutionCap > 0 && solutionCounter.get() >= solutionCap) {
                throw new IllegalStateException(
                        "Query produced more than " + solutionCap + " matches on " + instance.name
                                + " and was abandoned. Narrow it — add a WHERE(MAXDURATION(...)) or a "
                                + "tighter zone/entity, or avoid an OR whose branches hold for most of the "
                                + "clip — or raise the cap with -D" + MAX_SOLUTIONS_PROPERTY + "=<n> "
                                + "(0 disables it).");
            }

            // a game with fewer than ATLEAST or more than ATMOST answers gives none; otherwise it
            // gives its first SEARCH(k) answers, in the order the search found them
            int found = solutionCounter.get();
            List<Result> ofThisGame = r.subList(r.size() - found, r.size());
            if ((atLeast >= 0 && found < atLeast) || (atMost >= 0 && found > atMost)) {
                ofThisGame.clear();
            } else if (found > returned) {
                ofThisGame.subList(returned, found).clear();
            }
        }

        effort = new Effort(nodes, failures, modelNanos, searchNanos, timeouts);
        return order(r);
    }


    // ##################################
    // HELPER METHODS FOR MODELING EVENTS
    // ##################################


    /**
     * Models {@code event} on {@code event_interval}: an atomic event, or an AND or OR whose members
     * may be groups themselves. {@code groups} are the ANDs and ORs holding the event, outermost
     * first. A member of an AND happens exactly when the AND does; exactly one branch of an OR
     * happens when the OR does. So everything under an OR branch is optional.
     */
    private void modelEvent(Event event, List<Event> groups, CPSolver cp, AtomicInteger counterEvent, CPIntervalVar event_interval,
                            int ball_idx, int n, int[][] positionZones, int[][] positionBox_x,
                            int[][] positionBox_y, boolean total_circle, ArrayList<ExtendedCPVar> extVars, ArrayList<CPIntervalVar> intervals,
                            boolean total_rectangle, AtomicInteger counterVars, int[] teams,
                            Map<String, CPIntVar> IDENTIFIERS, int total_xcenter, int[] possession,
                            int total_ycenter, int total_radius, int total_xtop, int total_ytop,
                            int total_w, int total_h, int nbZones, int total_during_from, int total_during_to) {

        boolean optional = mayBeAbsent.contains(event_interval);

        if (event instanceof AndEvent andEvent) {

            extVars.add(new ExtendedCPVar(
                    event_interval,
                    counterVars.incrementAndGet(),
                    "AND_interval",
                    counterEvent.get(),
                    andEvent.isNegated,
                    andEvent.children().size()));

            // a temporal WHERE on the AND bounds its intersection
            postTemporalWhere(andEvent, event_interval);

            counterEvent.incrementAndGet();
            List<Event> inner = within(groups, andEvent);

            CPIntVar[] childrenStarts = new CPIntVar[andEvent.children().size()];
            CPIntVar[] childrenEnds = new CPIntVar[andEvent.children().size()];

            int childIdx = 0;

            for (Event children : andEvent.children()) {
                // a member happens exactly when the AND does
                CPIntervalVar childInterval;
                if (optional) {
                    childInterval = makeOptionalInterval(cp, 0, n + 1);
                    cp.post(eq(childInterval.status(), event_interval.status()));
                } else {
                    childInterval = makeIntervalVar(cp, false, 0, n + 1);
                }
                intervals.add(childInterval);
                childrenStarts[childIdx] = startOf(childInterval);
                childrenStarts[childIdx].removeAbove(n + 1);
                childrenEnds[childIdx] = endOf(childInterval);
                childrenEnds[childIdx].removeAbove(n + 1);

                modelEvent(children, inner, cp, counterEvent, childInterval, ball_idx, n,
                        positionZones, positionBox_x, positionBox_y, total_circle, extVars, intervals, total_rectangle,
                        counterVars, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                        total_radius, total_xtop, total_ytop, total_w, total_h, nbZones, total_during_from,
                        total_during_to);

                childIdx++;
            }

            // the AND spans the intersection of its members; when it is absent, so are they, and
            // every bound is the same ABSENT value
            cp.post(eq(startOf(event_interval), max(childrenStarts)));
            cp.post(eq(endOf(event_interval), minimum(childrenEnds)));

        } else if (event instanceof OrEvent orEvent) {

            extVars.add(new ExtendedCPVar(
                    event_interval,
                    counterVars.incrementAndGet(),
                    "OR_interval",
                    counterEvent.get(),
                    orEvent.isNegated,
                    orEvent.children().size()));

            // a temporal WHERE on the OR bounds its chosen branch
            postTemporalWhere(orEvent, event_interval);

            counterEvent.incrementAndGet();
            List<Event> inner = within(groups, orEvent);

            CPIntVar[] childrenStarts = new CPIntVar[orEvent.children().size()];
            CPIntVar[] childrenEnds = new CPIntVar[orEvent.children().size()];

            int childIdx = 0;
            CPBoolVar[] statuses = new CPBoolVar[orEvent.children().size()];
            for (Event children : orEvent.children()) {
                CPIntervalVar childInterval = makeOptionalInterval(cp, 0, n + 1);
                intervals.add(childInterval);
                childrenStarts[childIdx] = startOr(childInterval, n+1);
                childrenStarts[childIdx].removeAbove(n + 1);
                childrenEnds[childIdx] = endOr(childInterval, 0);
                childrenEnds[childIdx].removeAbove(n + 1);
                statuses[childIdx] = childInterval.status();

                // A branch that cannot hold in this game is switched off, not fatal: what it added
                // to the answer is dropped, and every interval in it is absent.
                int firstEvent = counterEvent.get();
                int firstExtVar = extVars.size();
                int firstInterval = intervals.size() - 1;
                try {
                    modelEvent(children, inner, cp, counterEvent, childInterval, ball_idx, n,
                            positionZones, positionBox_x, positionBox_y, total_circle, extVars, intervals, total_rectangle,
                            counterVars, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                            total_radius, total_xtop, total_ytop, total_w, total_h, nbZones, total_during_from,
                            total_during_to);
                } catch (InconsistencyException e) {
                    extVars.subList(firstExtVar, extVars.size()).clear();
                    extVars.add(new ExtendedCPVar(childInterval, counterVars.incrementAndGet(), "ABSENT_BRANCH", firstEvent, false));
                    for (CPIntervalVar itv : intervals.subList(firstInterval, intervals.size())) itv.setAbsent();
                    counterEvent.set(firstEvent + eventCount(children));
                }

                childIdx++;
            }

            // exactly one branch happens when the OR does, and the OR spans it
            if (optional) {
                // when the OR is absent, so are its branches: its bounds take the same defaults
                cp.post(eq(sum(statuses), event_interval.status()));
                cp.post(eq(startOr(event_interval, n + 1), minimum(childrenStarts)));
                cp.post(eq(endOr(event_interval, 0), max(childrenEnds)));
            } else {
                cp.post(eq(sum(statuses), 1));
                cp.post(eq(startOf(event_interval), minimum(childrenStarts)));
                cp.post(eq(endOf(event_interval), max(childrenEnds)));
            }

        } else {
            executeStep(event, groups, cp, counterEvent, event_interval, ball_idx, n,
                    positionZones, positionBox_x, positionBox_y, total_circle, extVars, intervals, total_rectangle,
                    counterVars, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                    total_radius, total_xtop, total_ytop, total_w, total_h, nbZones, total_during_from, total_during_to);
        }
    }

    /** {@code groups} followed by {@code group}. */
    private static List<Event> within(List<Event> groups, Event group) {
        List<Event> inner = new ArrayList<>(groups);
        inner.add(group);
        return inner;
    }

    /** How many events {@code event} is made of, itself and every group or atomic event in it. */
    private static int eventCount(Event event) {
        List<Event> children = event instanceof AndEvent and ? and.children()
                : event instanceof OrEvent or ? or.children() : List.of();
        return 1 + children.stream().mapToInt(Query::eventCount).sum();
    }


    /**
     * Models an atomic event. {@code groups} are the ANDs and ORs holding it, outermost first, none
     * for a step of the sequence: their spatial WHERE and their MINRANGE apply to each atomic event
     * in them.
     */
    private void executeStep(Event event, List<Event> groups, CPSolver cp, AtomicInteger counterEvent, CPIntervalVar eventInterval,
                             int ball_idx, int n, int[][] positionZones, int[][] positionBox_x,
                             int[][] positionBox_y, boolean total_circle, ArrayList<ExtendedCPVar> extVars, ArrayList<CPIntervalVar> intervals,
                             boolean total_rectangle, AtomicInteger counterVars,
                             int[] teams, Map<String, CPIntVar> IDENTIFIERS, int total_xcenter, int[] possession,
                             int total_ycenter, int total_radius, int total_xtop, int total_ytop,
                             int total_w, int total_h, int nbZones, int total_during_from, int total_during_to) {

        Action action = event.action();
        Entity subject = event.subject();
        int event_radius = event.radius;
        int event_xcenter = event.xcenter;
        int event_ycenter = event.ycenter;
        int event_xtop = event.xtop;
        int event_ytop = event.ytop;
        int event_w = event.w;
        int event_h = event.h;
        // a MINRANGE on an AND or OR holding the event applies to each atomic event in it
        boolean minrange = event.isMinrange || groups.stream().anyMatch(g -> g.isMinrange);
        Object payload = action.payload;
        boolean event_circle = (event_radius != -1);
        boolean event_rectangle = (event_w != -1 && event_h != -1);
        boolean isNegated = event.isNegated;

        ExtendedCPVar event_interval_ext = new ExtendedCPVar(
                eventInterval,
                counterVars.incrementAndGet(),
                action.name,
                counterEvent.get(),
                isNegated
        );

        extVars.add(event_interval_ext);

        postTemporalWhere(event, eventInterval);

        // what the WHERE of the event, of the ANDs and ORs holding it, and of the whole query asks of
        // each frame: the shapes of RECTANGLE and RADIUS, and the window of DURING
        List<Shape> shapes = whereShapes(event_circle, event_rectangle, total_circle, total_rectangle,
                event_xcenter, event_ycenter, event_radius, event_xtop, event_ytop, event_w, event_h,
                total_xcenter, total_ycenter, total_radius, total_xtop, total_ytop, total_w, total_h);
        for (Event group : groups) shapes.addAll(shapesOf(group));
        FrameWhere where = frameWhere(shapes, event, groups, total_during_from, total_during_to);

        switch (action.name) {

            //BALL EVENTS

            case "BALL_MOVE_TO" -> model_BALL_MOVE_TO(cp, isNegated, payload, eventInterval, ball_idx, n,
                    positionZones, positionBox_x, positionBox_y, where, minrange);

            //PLAYER EVENTS

            case "PASS_TO" ->
                    model_PASS_TO(cp, counterEvent, isNegated, payload, eventInterval, ball_idx, n, possession,
                            positionZones, positionBox_x, positionBox_y, where, subject, teams,
                            IDENTIFIERS, counterVars, action, extVars, minrange);

            case "PLAYER_MOVE_TO" -> model_PLAYER_MOVE_TO(cp, counterEvent, isNegated, eventInterval, where,
                    subject, teams, positionBox_x, positionBox_y, positionZones, payload, n, IDENTIFIERS,
                    counterVars, action, extVars, minrange);

            //TEAM EVENTS

            case "TEAM_MOVE_TO" ->
                    model_TEAM_MOVE_TO(cp, counterEvent, isNegated, eventInterval, where,
                            subject, teams, positionBox_x, positionBox_y, positionZones, payload, n, IDENTIFIERS,
                            counterVars, action, extVars, minrange);

            //TEAM + PLAYER EVENTS

            case "POSSESSION" ->
                    model_POSSESSION(cp, counterEvent, isNegated, eventInterval, ball_idx, n,
                            possession, where, subject, IDENTIFIERS, counterVars, action, extVars,
                            teams, positionBox_x, positionBox_y, positionZones);

            // TEAM + PLAYER + BALL EVENTS

            case "POSITION" ->
                    model_POSITION(cp, counterEvent, isNegated, eventInterval, where, subject, teams,
                            positionBox_x, positionBox_y, positionZones, payload, n, IDENTIFIERS, counterVars,
                            action, extVars, ball_idx);

        }
        counterEvent.incrementAndGet();
    }


    private void
    model_BALL_MOVE_TO(CPSolver cp, boolean isNegated, Object payload, CPIntervalVar eventInterval,
                       int ball_idx, int n, int[][] positionZones, int[][] positionBox_x,
                       int[][] positionBox_y, FrameWhere where, boolean minrange) {

        //ball movement logic

        int zone_start = ((int[]) payload)[0];
        int zone_end = ((int[]) payload)[1];
        // SELECT and MOVETO refuse a negated move and a move to the same zone
        // the ball moves as a group of one: 1 in zone_start, 2 in zone_end, 0 elsewhere
        postMove(cp, eventInterval, groupWord(new int[]{ball_idx}, zone_start, zone_end, positionZones, n), minrange, n);

        // the WHERE: the ball is never seen outside the shapes while it moves
        postThroughout(cp, eventInterval, where,
                insideWord(where, new int[]{ball_idx}, positionBox_x, positionBox_y, positionZones, n));
    }

    public void model_PASS_TO(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, Object payload, CPIntervalVar eventInterval,
                              int ball_idx, int n, int[] possession, int[][] positionZones, int[][] positionBox_x,
                              int[][] positionBox_y, FrameWhere where,
                              Entity subject, int[] teams, Map<String, CPIntVar> IDENTIFIERS,
                              AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, boolean minrange) {


        Player player_from = (Player) subject;
        Player player_to = (Player) payload;

        CPIntVar passer_id = makeIntVar(cp, teams.length);
        CPIntVar receiver_id = makeIntVar(cp, teams.length);

        // possession at the first and last frame of the event; ABSENT keeps these in range when the
        // interval is an OR branch that is switched off, and every use of them is guarded below
        CPIntVar possessionAtStart = element(possession, startOf(eventInterval));
        CPIntVar possessionAtEnd = element(possession, minus(endOf(eventInterval), 1));

        if (IDENTIFIERS.get(identityKey(player_from)) == null) {
            if (!isNegated) passer_id = boundWhenPresent(cp, eventInterval, possessionAtStart, teams.length);
            rememberIdentity(IDENTIFIERS, identityKey(player_from), passer_id, eventInterval);
        } else {
            passer_id = IDENTIFIERS.get(identityKey(player_from));
            noteIdentityUse(identityKey(player_from), eventInterval);
            if (!isNegated) postWhenPresent(cp, eventInterval, isEq(passer_id, possessionAtStart));

        }

        if (IDENTIFIERS.get(identityKey(player_to)) == null) {
            if (!isNegated) receiver_id = boundWhenPresent(cp, eventInterval, possessionAtEnd, teams.length);
            rememberIdentity(IDENTIFIERS, identityKey(player_to), receiver_id, eventInterval);
        } else {
            receiver_id = IDENTIFIERS.get(identityKey(player_to));
            noteIdentityUse(identityKey(player_to), eventInterval);
            if (!isNegated) postWhenPresent(cp, eventInterval, isEq(receiver_id, possessionAtEnd));
        }

        ExtendedCPVar passer_id_ext = new ExtendedCPVar(
                passer_id,
                counterVars.incrementAndGet(),
                action.name + "_passer_id",
                counterEvent.get(),
                isNegated
        );
        ExtendedCPVar rec_index_ext = new ExtendedCPVar(
                receiver_id,
                counterVars.incrementAndGet(),
                action.name + "_receiver_id",
                counterEvent.get(),
                isNegated
        );

        extVars.add(passer_id_ext);
        extVars.add(rec_index_ext);

        Set<Integer> passersIds = new HashSet<>();
        Set<Integer> receiversIds = new HashSet<>();
        Set<Integer> leftTeamIds = new HashSet<>();
        Set<Integer> rightTeamIds = new HashSet<>();

        for (int i = 0; i < teams.length; i++) {
            if (teams[i] == 0) leftTeamIds.add(i);
            else if (teams[i] == 1) rightTeamIds.add(i);
        }

        if (player_from.id() != null) {
            passersIds.add(player_from.id());
            postWhenPresent(cp, eventInterval, isEq(passer_id, player_from.id()));
        } else if (player_from.team() != null) {
            int team = player_from.team().equals("left") ? 0 : 1;
            for (int i = 0; i < teams.length; i++) {
                if (teams[i] == team) {
                    passersIds.add(i);
                }
            }
        } else {
            for (int i = 0; i < teams.length; i++) {
                if (teams[i] == 0 || teams[i] == 1) {
                    passersIds.add(i);
                }
            }
        }

        if (player_to.id() != null) {
            receiversIds.add(player_to.id());
            postWhenPresent(cp, eventInterval, isEq(receiver_id, player_to.id()));
        } else if (player_to.team() != null) {
            int team = player_to.team().equals("left") ? 0 : 1;
            for (int i = 0; i < teams.length; i++) {
                if (teams[i] == team) {
                    receiversIds.add(i);
                }
            }
        } else {
            for (int i = 0; i < teams.length; i++) {
                if (teams[i] == 0 || teams[i] == 1) {
                    receiversIds.add(i);
                }
            }
        }

        CPBoolVar[] isPasserLeft = new CPBoolVar[leftTeamIds.size()];
        CPBoolVar[] isReceiverLeft = new CPBoolVar[leftTeamIds.size()];
        int c = 0;
        for (int i : leftTeamIds) {
            isPasserLeft[c] = isEq(passer_id, i);
            isReceiverLeft[c] = isEq(receiver_id, i);
            c++;
        }
        CPBoolVar passerIsLeft = isEq(sum(isPasserLeft), 1);
        CPBoolVar receiverIsLeft = isEq(sum(isReceiverLeft), 1);

        CPBoolVar[] isPasserRight = new CPBoolVar[rightTeamIds.size()];
        CPBoolVar[] isReceiverRight = new CPBoolVar[rightTeamIds.size()];
        c = 0;
        for (int i : rightTeamIds) {
            isPasserRight[c] = isEq(passer_id, i);
            isReceiverRight[c] = isEq(receiver_id, i);
            c++;
        }
        CPBoolVar passerIsRight = isEq(sum(isPasserRight), 1);
        CPBoolVar receiverIsRight = isEq(sum(isReceiverRight), 1);

        postWhenPresent(cp, eventInterval, isEq(passerIsRight, receiverIsRight));
        postWhenPresent(cp, eventInterval, isEq(passerIsLeft, receiverIsLeft));


        // SELECT refuses a negated pass
        if (minrange) {
            //cp.post(new SubstringRegular(possession, eventInterval, Automaton.A_0STAR_B(teams.length, passersIds, receiversIds)));
            cp.post(new SubstringRegular(possession, eventInterval, Automaton.A_0PLUS_B(teams.length, passersIds, receiversIds)));
        } else {
            // n + 2: a pass may run from the first frame to the last one, plus its two delimiters
            CPIntervalVar paddedInterval = paddedFor(cp, eventInterval, n + 2);
            int[] paddedArray = Automaton.pad(possession);
            //cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.PAD_APLUS_PADSTAR_BPLUS_PAD(teams.length, passersIds, receiversIds)));
            cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.PAD_APLUS_0PLUS_BPLUS_PAD(teams.length, passersIds, receiversIds)));
            linkPadded(cp, eventInterval, paddedInterval);
        }


        // the WHERE: the ball is never seen outside the shapes during the pass
        postThroughout(cp, eventInterval, where,
                insideWord(where, new int[]{ball_idx}, positionBox_x, positionBox_y, positionZones, n));
    }

    public void model_PLAYER_MOVE_TO(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntervalVar eventInterval,
                                     FrameWhere where, Entity subject, int[] teams, int[][] positionBox_x, int[][] positionBox_y,
                                     int[][] positionZones, Object payload, int n, Map<String, CPIntVar> IDENTIFIERS,
                                     AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, boolean minrange) {


        int zone_start = ((int[]) payload)[0];
        int zone_end = ((int[]) payload)[1];
        // SELECT and MOVETO refuse a negated move and a move to the same zone

        Player player = (Player) subject;

        CPIntVar player_id = playerIdentity(cp, player, teams, IDENTIFIERS, eventInterval);

        ExtendedCPVar player_id_ext = new ExtendedCPVar(
                player_id,
                counterVars.incrementAndGet(),
                action.name + "_player_id",
                counterEvent.get(),
                isNegated
        );
        extVars.add(player_id_ext);

        // one candidate per player: the player moves as a group of one, and, under a WHERE, is
        // never seen outside the shapes and stays in the DURING window
        int[] candidates = candidatesOf(player_id);
        List<CandidateMatch> matches = new ArrayList<>();
        for (int pl_id : candidates) {
            int[] word = groupWord(new int[]{pl_id}, zone_start, zone_end, positionZones, n);
            int[] inside = insideWord(where, new int[]{pl_id}, positionBox_x, positionBox_y, positionZones, n);
            matches.add(window -> {
                postMove(cp, window, word, minrange, n);
                postThroughout(cp, window, where, inside);
            });
        }
        linkIdentity(cp, eventInterval, player_id, candidates, chooseCandidate(cp, eventInterval, matches, n));
    }

    public void model_TEAM_MOVE_TO(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntervalVar eventInterval,
                                   FrameWhere where, Entity subject, int[] teams, int[][] positionBox_x, int[][] positionBox_y,
                                   int[][] positionZones, Object payload, int n, Map<String, CPIntVar> IDENTIFIERS,
                                   AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, boolean minrange) {


        int[] zonesAndK = (int[]) payload;
        int zone_start = zonesAndK[0];
        int zone_end = zonesAndK[1];
        int k = zonesAndK[2];
        // SELECT and MOVETO refuse a negated move, a move to the same zone and k < 1

        // A group of k members is in a zone when all of them are, and the team moves when such a
        // group does: a Player MOVE_TO read on the group word (1: all in zone_start, 2: all in
        // zone_end, 0: anything else). One candidate per group, for each identity of the wildcard
        // members; groups that are never all in zone_start before being all in zone_end are skipped.
        TeamMembers members = teamMembers(cp, (Team) subject, teams, IDENTIFIERS, eventInterval);
        List<Group> groups = movingGroups(members, k, zone_start, zone_end, positionZones, n);
        List<CandidateMatch> matches = new ArrayList<>();
        for (Group group : groups) {
            int[] word = groupWord(group.players(), zone_start, zone_end, positionZones, n);
            // the WHERE: none of the k members that move is seen outside the shapes
            int[] inside = insideWord(where, group.players(), positionBox_x, positionBox_y, positionZones, n);
            matches.add(window -> {
                postMove(cp, window, word, minrange, n);
                postThroughout(cp, window, where, inside);
            });
        }
        CPIntVar chosen = chooseCandidate(cp, eventInterval, matches, n);

        // a wildcard named elsewhere is never one of the fixed members, and stands for its player
        // while its slot is in the chosen group
        for (int w = 0; w < members.wildcardSlots().length; w++) {
            CPIntVar identity = members.identities()[w];
            if (identity == null) continue;
            for (int id : members.template()) {
                if (id >= 0) postWhenPresent(cp, eventInterval, not(isEq(identity, id)));
            }
            for (int i = 0; i < groups.size(); i++) {
                int[] slots = groups.get(i).slots();
                for (int j = 0; j < k; j++) {
                    if (slots[j] != members.wildcardSlots()[w]) continue;
                    CPBoolVar standsFor = isEq(identity, groups.get(i).players()[j]);
                    if (chosen == null) postWhenPresent(cp, eventInterval, standsFor);
                    else impliesWhenPresent(cp, eventInterval, isEq(chosen, i), standsFor);
                }
            }
        }

        // the answer reports the k members that move; an anonymous one is still another player than
        // every named one. Where the slot is not anonymous, it takes a negative value of its own, so
        // that two anonymous members that do not take part are never equal
        for (int j = 0; j < k; j++) {
            final int position = j;
            int[] mover = groups.stream().mapToInt(g -> g.players()[position]).toArray();
            extVars.add(new ExtendedCPVar(selected(cp, mover, chosen), counterVars.incrementAndGet(),
                    action.name + "_player_id", counterEvent.get(), false));
            final int nobody = -1 - anonymousPlayers.size();
            int[] anonymous = groups.stream()
                    .mapToInt(g -> members.isAnonymous(g.slots()[position]) ? g.players()[position] : nobody).toArray();
            if (chosen != null && Arrays.stream(anonymous).anyMatch(v -> v >= 0)) {
                anonymousPlayers.add(new AnonymousPlayer(element(anonymous, chosen), eventInterval));
            } else if (chosen == null && anonymous[0] >= 0) {
                anonymousPlayers.add(new AnonymousPlayer(constant(cp, anonymous[0]), eventInterval));
            }
        }
    }

    public void model_POSITION(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntervalVar eventInterval,
                               FrameWhere where, Entity subject, int[] teams, int[][] positionBox_x,
                               int[][] positionBox_y, int[][] positionZones, Object payload, int n,
                               Map<String, CPIntVar> IDENTIFIERS, AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, int ball_idx) {

        if (subject instanceof Player player) {
            int[] zones = (int[]) payload;   // POSITION refuses an empty zone list
            Set<Integer> zoneSet = Arrays.stream(zones).boxed().collect(Collectors.toSet());

            CPIntVar player_id = playerIdentity(cp, player, teams, IDENTIFIERS, eventInterval);

            ExtendedCPVar player_id_ext = new ExtendedCPVar(
                    player_id,
                    counterVars.incrementAndGet(),
                    action.name + "_player_id",
                    counterEvent.get(),
                    isNegated
            );
            extVars.add(player_id_ext);

            // one candidate per player: a maximal stay in (negated: out of) the zones on the frames the
            // WHERE keeps, those where the player is not seen outside the shapes, in the DURING window
            int[] candidates = candidatesOf(player_id);
            List<CandidateMatch> matches = new ArrayList<>();
            for (int pl_id : candidates) {
                int[] word = restrict(zoneWord(positionZones, pl_id, zoneSet, isNegated, n),
                        insideWord(where, new int[]{pl_id}, positionBox_x, positionBox_y, positionZones, n), where);
                matches.add(window -> postPadded(cp, window, word, Automaton.NOTA_APLUS_NOTA(1, Set.of(1)), n));
            }
            linkIdentity(cp, eventInterval, player_id, candidates, chooseCandidate(cp, eventInterval, matches, n));

        } else if (subject instanceof Team team) {

            int[] zonesAndK = (int[]) payload;
            int[] zones = Arrays.copyOf(zonesAndK, zonesAndK.length - 1);   // POSITION refuses no zone
            int k = zonesAndK[zonesAndK.length - 1];                           // and K refuses k < 1
            Set<Integer> zoneSet = Arrays.stream(zones).boxed().collect(Collectors.toSet());

            // The team is in the zones while at least k of its members are (negated: while fewer than
            // k are), whichever members they are on each frame: one maximal run of the count word, for
            // each identity of the wildcard members. Under a WHERE, a member counts only while it is
            // not seen outside the shapes, and only the frames of the DURING window are kept.
            TeamMembers members = teamMembers(cp, team, teams, IDENTIFIERS, eventInterval);
            int[][] inside = where.shapes().isEmpty() ? null
                    : insideByFrame(where.shapes(), positionBox_x, positionBox_y, positionZones, n);

            List<int[]> rows = teamCandidates(members);
            List<CandidateMatch> matches = new ArrayList<>();
            for (int[] row : rows) {
                int[] word = restrict(countWord(row, k, zoneSet, isNegated, positionZones, inside, n), null, where);
                matches.add(window -> postPadded(cp, window, word, Automaton.NOTA_APLUS_NOTA(1, Set.of(1)), n));
            }
            CPIntVar chosen = chooseCandidate(cp, eventInterval, matches, n);
            reportMembers(cp, members, linkWildcards(cp, eventInterval, members, rows, chosen), extVars, counterVars,
                    action, counterEvent);

        } else { //ball
            int[] zones = (int[]) payload;   // POSITION refuses an empty zone list
            Set<Integer> zoneSet = Arrays.stream(zones).boxed().collect(Collectors.toSet());
            // one maximal stay of the ball in (negated: out of) the zones, on the frames the WHERE keeps
            int[] word = restrict(zoneWord(positionZones, ball_idx, zoneSet, isNegated, n),
                    insideWord(where, new int[]{ball_idx}, positionBox_x, positionBox_y, positionZones, n), where);
            postPadded(cp, eventInterval, word, Automaton.NOTA_APLUS_NOTA(1, Set.of(1)), n);
        }
    }

    public void model_POSSESSION(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntervalVar eventInterval,
                                 int ball_idx, int n, int[] possession, FrameWhere where,
                                 Entity subject, Map<String, CPIntVar> IDENTIFIERS, AtomicInteger counterVars, Action action,
                                 ArrayList<ExtendedCPVar> extVars, int[] teams, int[][] positionBox_x, int[][] positionBox_y,
                                 int[][] positionZones) {

        // the WHERE tests the ball: a possession keeps the frames where the ball is not seen outside
        // the shapes, in the DURING window
        int[] ballInside = insideWord(where, new int[]{ball_idx}, positionBox_x, positionBox_y, positionZones, n);

        if (subject instanceof Player player) {

            CPIntVar player_id = makeIntVar(cp, teams.length);

            // the holder of the ball on the frames the WHERE keeps, nobody (0) on the others
            int[] held = restrict(possession, ballInside, where);

            // a positive event reads p off the sequence: the holder of the ball on its first frame
            CPIntVar possessionAtStart = isNegated ? null : element(held, startOf(eventInterval));

            if (IDENTIFIERS.get(identityKey(player)) == null) {
                if (!isNegated) player_id = boundWhenPresent(cp, eventInterval, possessionAtStart, teams.length);
                rememberIdentity(IDENTIFIERS, identityKey(player), player_id, eventInterval);
            } else {
                player_id = IDENTIFIERS.get(identityKey(player));
                noteIdentityUse(identityKey(player), eventInterval);
                if (!isNegated) postWhenPresent(cp, eventInterval, isEq(player_id, possessionAtStart));
            }

            ExtendedCPVar player_id_ext = new ExtendedCPVar(
                    player_id,
                    counterVars.incrementAndGet(),
                    action.name + "_player_id",
                    counterEvent.get(),
                    isNegated
            );
            extVars.add(player_id_ext);

            //possession logic

            Set<Integer> validIds = new HashSet<>();

            if (player.id() != null) {
                validIds.add(player.id());
                postWhenPresent(cp, eventInterval, isEq(player_id, player.id()));
            } else if (player.team() != null) {
                int team = player.team().equals("left") ? 0 : 1;
                for (int i = 0; i < teams.length; i++) {
                    if (teams[i] == team) {
                        validIds.add(i);
                    } else {
                        player_id.remove(i);
                    }
                }
            } else {
                for (int i = 0; i < teams.length; i++) {
                    if (teams[i] == 0 || teams[i] == 1) {
                        validIds.add(i);
                    } else {
                        player_id.remove(i);
                    }
                }
            }
            final CPIntVar identity = player_id;
            validIds.removeIf(id -> !identity.contains(id));

            if (isNegated) {
                // p does not hold the ball: one candidate per player, as for Player POSITION. A single
                // automaton cannot tell whose stay it reads: one that starts on the first frame only
                // shows it at its trailing delimiter.
                int[] candidates = validIds.stream().mapToInt(Integer::intValue).sorted().toArray();
                List<CandidateMatch> matches = new ArrayList<>();
                for (int pl_id : candidates) {
                    int[] notHeld = new int[n + 1];
                    for (int f = 1; f <= n; f++) notHeld[f] = possession[f] != pl_id ? 1 : 0;
                    int[] word = restrict(notHeld, ballInside, where);
                    matches.add(window -> postPadded(cp, window, word, Automaton.NOTA_APLUS_NOTA(1, Set.of(1)), n));
                }
                linkIdentity(cp, eventInterval, player_id, candidates, chooseCandidate(cp, eventInterval, matches, n));
            } else {
                // one maximal stay of a single candidate: a direct handover to another one ends it
                postPadded(cp, eventInterval, held, Automaton.NOTX_XPLUS_NOTX(teams.length, validIds), n);
            }

        } else if (subject instanceof Team team) {

            // The team has the ball from a member's touch to a member's touch, as long as nobody
            // outside the team touches it: the ball-free frames in between belong to it. One maximal
            // run of that word (negated: of the other frames) on the frames the WHERE keeps, for each
            // identity of the wildcard members. Such a run may start or end on a ball-free frame.
            TeamMembers members = teamMembers(cp, team, teams, IDENTIFIERS, eventInterval);

            List<int[]> rows = teamCandidates(members);
            List<CandidateMatch> matches = new ArrayList<>();
            for (int[] row : rows) {
                int[] word = restrict(teamPossessionWord(possession, row, isNegated, n), ballInside, where);
                matches.add(window -> postPadded(cp, window, word, Automaton.NOTA_APLUS_NOTA(1, Set.of(1)), n));
            }
            CPIntVar chosen = chooseCandidate(cp, eventInterval, matches, n);
            reportMembers(cp, members, linkWildcards(cp, eventInterval, members, rows, chosen), extVars, counterVars,
                    action, counterEvent);
        }
    }

    /** Creates an interval that may end up absent, and remembers it as such. */
    private CPIntervalVar makeOptionalInterval(CPSolver cp, int lengthMin, int lengthMax) {
        CPIntervalVar itv = makeIntervalVar(cp, lengthMin, lengthMax);
        mayBeAbsent.add(itv);
        return itv;
    }

    /** The intervals of the events naming each player identity. */
    private final Map<String, Set<CPIntervalVar>> identityUsers = new HashMap<>();

    /** Names of identities used by at least one event that always happens. */
    private final Set<String> alwaysPresentIdentities = new HashSet<>();

    /** Records {@code name -> var} as an identity introduced by {@code owner}. */
    private void rememberIdentity(Map<String, CPIntVar> identifiers, String name, CPIntVar var, CPIntervalVar owner) {
        identifiers.put(name, var);
        noteIdentityUse(name, owner);
    }

    /** Records that {@code owner} refers to the identity {@code name}. */
    private void noteIdentityUse(String name, CPIntervalVar owner) {
        identityUsers.computeIfAbsent(name, k -> Collections.newSetFromMap(new IdentityHashMap<>())).add(owner);
        if (!mayBeAbsent.contains(owner)) alwaysPresentIdentities.add(name);
    }

    /** Whether {@code itv} happens: null when it always does. */
    private CPBoolVar presenceOf(CPIntervalVar itv) {
        return itv != null && mayBeAbsent.contains(itv) ? itv.status() : null;
    }

    /**
     * Whether the identity {@code name} stands for a player: whenever one of the events naming it
     * happens. Null when one of them always does.
     */
    private CPBoolVar identityPresence(String name) {
        if (alwaysPresentIdentities.contains(name)) return null;
        CPBoolVar[] statuses = identityUsers.get(name).stream().map(CPIntervalVar::status).toArray(CPBoolVar[]::new);
        return statuses.length == 1 ? statuses[0] : not(isEq(sum(statuses), 0));
    }

    /** Posts {@code a != b}, but only while both happen ({@code null}: always). */
    private void postDistinctWhenBothPresent(CPSolver cp, CPBoolVar presentA, CPBoolVar presentB,
                                             CPIntVar a, CPIntVar b) {
        List<CPBoolVar> guards = new ArrayList<>(2);
        if (presentA != null) guards.add(presentA);
        if (presentB != null) guards.add(presentB);
        if (guards.isEmpty()) {
            cp.post(neq(a, b));
        } else {
            CPBoolVar bothHappen = isEq(sum(guards.toArray(CPBoolVar[]::new)), guards.size());
            cp.post(implies(bothHappen, not(isEq(a, b))));
        }
    }

    /** Start of {@code itv}, safe to call on an optional interval. */
    private CPIntVar startOf(CPIntervalVar itv) {
        return startVars.computeIfAbsent(itv, i -> mayBeAbsent.contains(i) ? startOr(i, ABSENT) : start(i));
    }

    /** End of {@code itv}, safe to call on an optional interval. */
    private CPIntVar endOf(CPIntervalVar itv) {
        return endVars.computeIfAbsent(itv, i -> mayBeAbsent.contains(i) ? endOr(i, ABSENT) : end(i));
    }

    /** Posts {@code b} unless {@code eventInterval} is an unselected branch */
    private void postWhenPresent(CPSolver cp, CPIntervalVar eventInterval, CPBoolVar b) {
        if (mayBeAbsent.contains(eventInterval)) cp.post(implies(eventInterval.status(), b));
        else cp.post(eq(b, 1));
    }

    /**
     * Posts {@code condition => consequence}, itself gated on {@code eventInterval} being present,
     * so an unselected branch imposes nothing even when the condition happens to hold.
     */
    private void impliesWhenPresent(CPSolver cp, CPIntervalVar eventInterval, CPBoolVar condition, CPBoolVar consequence) {
        if (mayBeAbsent.contains(eventInterval)) {
            cp.post(implies(isEq(sum(eventInterval.status(), condition), 2), consequence));
        } else {
            cp.post(implies(condition, consequence));
        }
    }

    /** A variable equal to {@code expr} while {@code eventInterval} is present, and free otherwise. */
    private CPIntVar boundWhenPresent(CPSolver cp, CPIntervalVar eventInterval, CPIntVar expr, int nbValues) {
        if (!mayBeAbsent.contains(eventInterval)) return expr;
        CPIntVar v = makeIntVar(cp, nbValues);
        postWhenPresent(cp, eventInterval, isEq(v, expr));
        return v;
    }

    /** Creates the padded helper interval that carries an automaton match for {@code eventInterval}. */
    private CPIntervalVar paddedFor(CPSolver cp, CPIntervalVar eventInterval, int lengthMax) {
        if (candidateWindows.contains(eventInterval)) {
            // a candidate window: optional, and not an event interval
            CPIntervalVar padded = makeIntervalVar(cp, 0, lengthMax);
            cp.post(eq(padded.status(), eventInterval.status()));
            return padded;
        }
        boolean optional = mayBeAbsent.contains(eventInterval);
        CPIntervalVar padded = optional ? makeOptionalInterval(cp, 0, lengthMax) : makeIntervalVar(cp, false, 0, lengthMax);
        if (optional) cp.post(eq(padded.status(), eventInterval.status()));
        return padded;
    }

    /**
     * Ties an event interval to its padded match: same start, end shifted back by the two pad
     * symbols. Conditioned on presence, so an unselected branch constrains nothing.
     */
    private void linkPadded(CPSolver cp, CPIntervalVar eventInterval, CPIntervalVar padded) {
        if (candidateWindows.contains(eventInterval)) {
            cp.post(new PaddedWindow(eventInterval, padded, 2));
            return;
        }
        CPIntVar s = startOf(eventInterval);
        CPIntVar e = endOf(eventInterval);
        CPIntVar paddedStart = startOf(padded);
        CPIntVar paddedEnd = minus(endOf(padded), 2);
        if (mayBeAbsent.contains(eventInterval)) {
            CPBoolVar present = eventInterval.status();
            cp.post(implies(present, isEq(s, paddedStart)));
            cp.post(implies(present, isEq(e, paddedEnd)));
        } else {
            cp.post(eq(s, paddedStart));
            cp.post(eq(e, paddedEnd));
        }
    }

    // #############
    // SPATIAL WHERE
    // #############

    /** One WHERE shape, tested on the fixed tracking data. */
    private interface Shape {
        boolean contains(int x, int y);
    }

    private static Shape circle(int squaredRadius, int xcenter, int ycenter) {
        return (x, y) -> {
            int dx = x - xcenter;
            int dy = y - ycenter;
            return dx * dx + dy * dy <= squaredRadius;
        };
    }

    /** The rectangle of a spatial WHERE: x in [xtop, xtop + w] and y in [ytop - h, ytop]. */
    private static Shape box(int xtop, int ytop, int w, int h) {
        return (x, y) -> x <= xtop + w && x >= xtop && y <= ytop && y >= ytop - h;
    }

    /** Posts that {@code window} only covers frames where {@code word} is 1. */
    private static void postInsideThroughout(CPSolver cp, int[] word, CPIntervalVar window) {
        cp.post(new SubstringRegular(word, window, Automaton.APLUS(1, Set.of(1))));
    }

    /**
     * The shapes of a spatial WHERE, of the event and of the whole query, the same for every
     * subject. A rectangle spans x in [xtop, xtop + |w|] and y in [ytop - |h|, ytop].
     */
    private static List<Shape> whereShapes(boolean event_circle, boolean event_rectangle, boolean total_circle,
                                           boolean total_rectangle, int event_xcenter, int event_ycenter,
                                           int event_radius, int event_xtop, int event_ytop, int event_w, int event_h,
                                           int total_xcenter, int total_ycenter, double total_radius, int total_xtop,
                                           int total_ytop, int total_w, int total_h) {
        List<Shape> shapes = new ArrayList<>();
        if (event_circle) shapes.add(circle((int) Math.pow(event_radius, 2), event_xcenter, event_ycenter));
        if (total_circle) shapes.add(circle((int) Math.pow(total_radius, 2), total_xcenter, total_ycenter));
        if (event_rectangle) shapes.add(box(event_xtop, event_ytop, Math.abs(event_w), Math.abs(event_h)));
        if (total_rectangle) shapes.add(box(total_xtop, total_ytop, Math.abs(total_w), Math.abs(total_h)));
        return shapes;
    }

    /** The shapes of the spatial WHERE written on {@code event} itself. */
    private static List<Shape> shapesOf(Event event) {
        return whereShapes(event.radius != -1, event.w != -1 && event.h != -1, false, false,
                event.xcenter, event.ycenter, event.radius, event.xtop, event.ytop, event.w, event.h,
                0, 0, 0, 0, 0, 0, 0);
    }

    /**
     * The temporal WHERE written on {@code event}, as bounds on {@code interval}: the event itself,
     * or, for an AND or OR, its intersection or its chosen branch.
     */
    private static void postTemporalWhere(Event event, CPIntervalVar interval) {
        if (event.timeStartMin != -1) {
            interval.setStartMin(event.timeStartMin);
        }
        if (event.timeStartMax != -1) {
            interval.setStartMax(event.timeStartMax);
        }
        if (event.timeEndMin != -1) {
            interval.setEndMin(event.timeEndMin + 1); //end is exclusive so > k means "last frame >= k"
        }
        if (event.timeEndMax != -1) {
            interval.setEndMax(event.timeEndMax + 1);
        }
        if (event.maxDuration != -1) {
            interval.setLengthMax(event.maxDuration);
        }
        if (event.minDuration != -1) {
            interval.setLengthMin(event.minDuration);
        }
    }

    /** Where an entity stands with respect to the shapes of a spatial WHERE on one frame. */
    private static final int OUTSIDE = 0, INSIDE = 1, UNTRACKED = 2;

    /**
     * INSIDE or OUTSIDE the shapes for entity {@code p} (a player or the ball) on frame {@code f},
     * or UNTRACKED when it is not seen there: then its position is unknown.
     */
    private static int where(List<Shape> shapes, int p, int f, int[][] positionBox_x, int[][] positionBox_y,
                             int[][] positionZones) {
        // every tracked entity has a zone; positionBox is indexed from 0 for frame 1
        if (zone(positionZones, p, f) == 0 || p >= positionBox_x.length) return UNTRACKED;
        for (Shape s : shapes) {
            if (!s.contains(positionBox_x[p][f - 1], positionBox_y[p][f - 1])) return OUTSIDE;
        }
        return INSIDE;
    }

    /** {@link #where} for every player on every frame, indexed by frame like the zone sequences. */
    private static int[][] insideByFrame(List<Shape> shapes, int[][] positionBox_x, int[][] positionBox_y,
                                         int[][] positionZones, int n) {
        int[][] inside = new int[positionBox_x.length][n + 1];
        for (int p = 0; p < positionBox_x.length; p++) {
            for (int f = 1; f <= n; f++) inside[p][f] = where(shapes, p, f, positionBox_x, positionBox_y, positionZones);
        }
        return inside;
    }

    /** Whether player {@code id} is seen outside the shapes on frame {@code f}: an untracked player is not. */
    private static boolean seenOutside(int[][] inside, int id, int f) {
        return id >= 0 && id < inside.length && inside[id][f] == OUTSIDE;
    }

    /**
     * 1 on the frames where none of the {@code ids} is seen outside the shapes; a frame where one of
     * them is not tracked neither confirms nor breaks a spatial WHERE. Frame 0, the dummy frame, is 0.
     */
    private static int[] notSeenOutside(List<Shape> shapes, int[] ids, int[][] positionBox_x, int[][] positionBox_y,
                                        int[][] positionZones, int n) {
        int[] word = new int[n + 1];
        for (int f = 1; f <= n; f++) {
            boolean ok = true;
            for (int id : ids) ok &= where(shapes, id, f, positionBox_x, positionBox_y, positionZones) != OUTSIDE;
            word[f] = ok ? 1 : 0;
        }
        return word;
    }

    /**
     * What a WHERE asks of each frame: to be inside the {@code shapes} (RECTANGLE, RADIUS) and in the
     * frames [{@code from}, {@code to}] of DURING ({@code from} = -1: no DURING). A state (POSITION,
     * POSSESSION) keeps the frames that meet it, so its runs are the maximal runs of the state and of
     * that condition; a move or a pass must meet it on every one of its frames.
     */
    private record FrameWhere(List<Shape> shapes, int from, int to) {
        boolean hasWindow() {
            return from != -1;
        }
    }

    /**
     * The frame conditions of an atomic event: {@code shapes}, and the frames that every DURING
     * allows, of the event, of the ANDs and ORs holding it and of the whole query.
     */
    private static FrameWhere frameWhere(List<Shape> shapes, Event event, List<Event> groups, int queryFrom, int queryTo) {
        List<int[]> windows = new ArrayList<>();
        windows.add(new int[]{event.duringFrom, event.duringTo});
        for (Event group : groups) windows.add(new int[]{group.duringFrom, group.duringTo});
        windows.add(new int[]{queryFrom, queryTo});
        int from = -1, to = -1;
        for (int[] window : windows) {
            if (window[0] == -1) continue;
            from = Math.max(from, window[0]);
            to = to == -1 ? window[1] : Math.min(to, window[1]);
        }
        return new FrameWhere(shapes, from, to);
    }

    /** {@link #notSeenOutside} for the shapes of {@code where}; null when it has none. */
    private static int[] insideWord(FrameWhere where, int[] ids, int[][] positionBox_x, int[][] positionBox_y,
                                    int[][] positionZones, int n) {
        return where.shapes().isEmpty() ? null : notSeenOutside(where.shapes(), ids, positionBox_x, positionBox_y, positionZones, n);
    }

    /**
     * A state under a WHERE: {@code word} with 0 on the frames where {@code inside} is 0 (none: no
     * shape) and on those outside the DURING window.
     */
    private static int[] restrict(int[] word, int[] inside, FrameWhere where) {
        if (inside == null && !where.hasWindow()) return word;
        int[] kept = word.clone();
        for (int f = 0; f < kept.length; f++) {
            boolean outside = inside != null && inside[f] == 0;
            boolean outOfWindow = where.hasWindow() && (f < where.from() || f > where.to());
            if (outside || outOfWindow) kept[f] = 0;
        }
        return kept;
    }

    /**
     * A move or a pass under a WHERE: {@code window} only covers frames where {@code inside} is 1
     * (none: no shape), within the DURING window.
     */
    private static void postThroughout(CPSolver cp, CPIntervalVar window, FrameWhere where, int[] inside) {
        if (inside != null) postInsideThroughout(cp, inside, window);
        if (where.hasWindow()) {
            window.setStartMin(where.from());
            window.setEndMax(where.to() + 1); // end is exclusive
        }
    }

    // ###########
    // TEAM EVENTS
    // ###########

    /** Most candidates a team event may try: identities of its wildcard members, or moving groups. */
    private static final int MAX_TEAM_CANDIDATES = 20_000;

    /** Most groups of members a team move may examine before keeping the candidates among them. */
    private static final int MAX_TEAM_GROUPS_TRIED = 200_000;

    /**
     * The members of a team event, slot by slot in the order of the query. A member known from the
     * query, by its ID or as the roster of a side, is a constant: it never enters the
     * identity map, so a team member may also be a player named elsewhere in the query. A wildcard
     * member that another event names too is an identity, shared with that event. A wildcard named
     * nowhere else is anonymous: only the set of players the team stands for matters, so it has no
     * variable of its own and adds no answer per identity, but it is still another player than
     * every named one.
     * {@code template} holds the fixed ids, -1 for each wildcard; wildcard w sits in slot
     * {@code wildcardSlots[w]}, has identity {@code identities[w]} (null when anonymous) and may
     * stand for the players {@code candidates[w]}, in increasing order.
     */
    private record TeamMembers(int[] template, int[] wildcardSlots, CPIntVar[] identities, int[][] candidates) {

        /** The wildcard in {@code slot}, -1 for a fixed member. */
        int wildcardAt(int slot) {
            for (int w = 0; w < wildcardSlots.length; w++) {
                if (wildcardSlots[w] == slot) return w;
            }
            return -1;
        }

        boolean isAnonymous(int slot) {
            int w = wildcardAt(slot);
            return w >= 0 && identities[w] == null;
        }
    }

    private TeamMembers teamMembers(CPSolver cp, Team team, int[] teams, Map<String, CPIntVar> IDENTIFIERS,
                                    CPIntervalVar eventInterval) {
        List<Integer> template = new ArrayList<>();
        Set<Integer> fixedIds = new HashSet<>();
        List<Integer> wildcardSlots = new ArrayList<>();
        List<CPIntVar> identities = new ArrayList<>();
        List<int[]> candidates = new ArrayList<>();
        if (Team.isSide(team.name())) {
            int side = team.name().equals("left") ? 0 : 1;
            for (int i = 1; i < teams.length; i++) {
                if (teams[i] == side && fixedIds.add(i)) template.add(i);
            }
        } else {
            for (Player player : team.players()) {
                if (player.id() != null) {
                    if (fixedIds.add(player.id())) template.add(player.id());
                    continue;
                }
                CPIntVar identity = null;
                int[] players;
                if (identityUses.getOrDefault(identityKey(player), 0) > 1) {
                    identity = playerIdentity(cp, player, teams, IDENTIFIERS, eventInterval);
                    if (identities.contains(identity)) continue;
                    players = candidatesOf(identity);
                } else {
                    players = IntStream.range(1, teams.length).filter(i -> onSide(player, teams[i])).toArray();
                }
                wildcardSlots.add(template.size());
                template.add(-1);
                identities.add(identity);
                candidates.add(players);
            }
        }
        return new TeamMembers(template.stream().mapToInt(Integer::intValue).toArray(),
                wildcardSlots.stream().mapToInt(Integer::intValue).toArray(),
                identities.toArray(CPIntVar[]::new), candidates.toArray(int[][]::new));
    }

    /**
     * Adds the members of a team to the answer, in the order of the query: the fixed ones, then
     * {@code wildcards[w]}, the player wildcard w stands for in the chosen candidate.
     */
    private static void reportMembers(CPSolver cp, TeamMembers members, CPIntVar[] wildcards, List<ExtendedCPVar> extVars,
                                      AtomicInteger counterVars, Action action, AtomicInteger counterEvent) {
        for (int slot = 0; slot < members.template().length; slot++) {
            int w = members.wildcardAt(slot);
            CPIntVar member = w < 0 ? constant(cp, members.template()[slot]) : wildcards[w];
            extVars.add(new ExtendedCPVar(member, counterVars.incrementAndGet(), action.name + "_player_id",
                    counterEvent.get(), false));
        }
    }

    /** A variable fixed to {@code value}, to report a known player. */
    private static CPIntVar constant(CPSolver cp, int value) {
        CPIntVar v = makeIntVar(cp, value + 1);
        v.fix(value);
        return v;
    }

    /**
     * The teams a team event may stand for: one row per way to give its wildcard members distinct
     * players that are not its fixed members, the players listed slot by slot. Rows that differ only
     * in how anonymous members share the same players are one team.
     */
    private static List<int[]> teamCandidates(TeamMembers members) {
        List<int[]> rows = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int[] slots = IntStream.range(0, members.template().length).toArray();
        assignWildcards(members, 0, members.template().clone(), row -> {
            if (seen.add(teamKey(members, slots, row))) {
                rows.add(row.clone());
                checkCandidateCount(rows.size(), "teams for its wildcard members");
            }
        });
        return rows;
    }

    private static void assignWildcards(TeamMembers members, int w, int[] row, java.util.function.Consumer<int[]> sink) {
        if (w == members.wildcardSlots().length) {
            sink.accept(row);
            return;
        }
        int slot = members.wildcardSlots()[w];
        for (int v : members.candidates()[w]) {
            boolean taken = false;
            for (int id : row) taken |= id == v; // a fixed member, or a wildcard given that player already
            if (taken) continue;
            row[slot] = v;
            assignWildcards(members, w + 1, row, sink);
            row[slot] = -1;
        }
    }

    /**
     * What tells two candidates apart: the set of players of their slots, and which player each
     * wildcard named elsewhere stands for. {@code players[i]} is the player of {@code slots[i]}.
     */
    private static String teamKey(TeamMembers members, int[] slots, int[] players) {
        StringBuilder key = new StringBuilder(Arrays.toString(Arrays.stream(players).sorted().toArray()));
        for (int i = 0; i < slots.length; i++) {
            int w = members.wildcardAt(slots[i]);
            if (w >= 0 && members.identities()[w] != null) key.append(' ').append(slots[i]).append('=').append(players[i]);
        }
        return key.toString();
    }

    private static void checkCandidateCount(int count, String what) {
        if (count > MAX_TEAM_CANDIDATES) {
            throw new IllegalArgumentException("A team event has more than " + MAX_TEAM_CANDIDATES + " " + what
                    + " to try. Give the IDs of its members, or use fewer anonymous members.");
        }
    }

    /**
     * Posts the match of one candidate on {@code window}. Without any match, a present window
     * fails and an optional one becomes absent.
     */
    private interface CandidateMatch {
        void post(CPIntervalVar window);
    }

    /**
     * {@code eventInterval} is the match of exactly one candidate. A single candidate is posted on
     * the event interval itself, and null is returned. Otherwise each candidate gets an optional
     * window, {@code eventInterval = windows[chosen]} with every other window absent
     * ({@link AlternativeElement}), and {@code chosen} is returned: a search variable, so every
     * answer is checked against the candidate it stands for.
     * <p>
     * A candidate without any match only makes its window absent, and its value leaves
     * {@code chosen}; nothing fails while the model is built. With no candidate left, the event
     * cannot happen. An unselected OR branch keeps {@code chosen} on the first candidate with a
     * match, so it adds no duplicate answer.
     */
    private CPIntVar chooseCandidate(CPSolver cp, CPIntervalVar eventInterval, List<CandidateMatch> candidates, int n) {
        if (candidates.isEmpty()) throw new InconsistencyException();
        if (candidates.size() == 1) {
            candidates.getFirst().post(eventInterval);
            return null;
        }
        CPIntervalVar[] windows = new CPIntervalVar[candidates.size()];
        int first = -1;
        for (int i = 0; i < windows.length; i++) {
            windows[i] = makeIntervalVar(cp, 0, n + 1); // optional
            candidateWindows.add(windows[i]);
            candidates.get(i).post(windows[i]);
            if (first < 0 && !windows[i].isAbsent()) first = i;
        }
        if (first < 0) throw new InconsistencyException();

        CPIntVar chosen = makeIntVar(cp, candidates.size());
        int absentValue = mayBeAbsent.contains(eventInterval) ? first : -1;
        cp.post(new AlternativeElement(eventInterval, windows, chosen, absentValue));
        decisionVars.add(chosen);
        return chosen;
    }

    /**
     * Ties each wildcard member to the player it stands for in the chosen candidate, whose team is
     * {@code rows.get(i)}, and returns those players: an identity must be that player, an
     * anonymous member is that player, still another one than every named player.
     */
    private CPIntVar[] linkWildcards(CPSolver cp, CPIntervalVar eventInterval, TeamMembers members, List<int[]> rows,
                                     CPIntVar chosen) {
        CPIntVar[] wildcards = new CPIntVar[members.wildcardSlots().length];
        for (int w = 0; w < wildcards.length; w++) {
            final int slot = members.wildcardSlots()[w];
            wildcards[w] = selected(cp, rows.stream().mapToInt(row -> row[slot]).toArray(), chosen);
            if (members.identities()[w] != null) {
                postWhenPresent(cp, eventInterval, isEq(members.identities()[w], wildcards[w]));
                wildcards[w] = members.identities()[w];
            } else {
                anonymousPlayers.add(new AnonymousPlayer(wildcards[w], eventInterval));
            }
        }
        return wildcards;
    }

    /** {@code values[chosen]}, or the only value when there is no choice. */
    private static CPIntVar selected(CPSolver cp, int[] values, CPIntVar chosen) {
        return chosen == null ? constant(cp, values[0]) : element(values, chosen);
    }

    /**
     * Posts that {@code window} is a match of {@code automaton} on {@code word}, padded with 0 on
     * both sides so that a match can start on the first frame and end on the last one.
     */
    private void postPadded(CPSolver cp, CPIntervalVar window, int[] word, Automaton automaton, int n) {
        // n + 2: a match may cover every frame of the clip, plus its two delimiters
        CPIntervalVar padded = paddedFor(cp, window, n + 2);
        cp.post(new SubstringRegular(Automaton.pad(word), padded, automaton));
        linkPadded(cp, window, padded);
    }

    /** The zone of player {@code id} on frame {@code f}; 0 when it is not tracked there. */
    private static int zone(int[][] positionZones, int id, int f) {
        return id >= 0 && id < positionZones.length ? positionZones[id][f] : 0;
    }

    /**
     * Team POSITION: 1 on the frames where at least {@code k} of the {@code ids} are in the zones
     * and, given {@code inside}, are not seen outside the shapes there. Negated, 1 on every other
     * frame: fewer than {@code k} members are counted, untracked members never being. Frame 0, the
     * dummy frame, is 0.
     */
    private static int[] countWord(int[] ids, int k, Set<Integer> zones, boolean isNegated, int[][] positionZones,
                                   int[][] inside, int n) {
        int[] word = new int[n + 1];
        for (int f = 1; f <= n; f++) {
            int count = 0;
            for (int id : ids) {
                boolean counts = zones.contains(zone(positionZones, id, f));
                if (counts && (inside == null || !seenOutside(inside, id, f))) count++;
            }
            word[f] = (count >= k) != isNegated ? 1 : 0;
        }
        return word;
    }

    /**
     * Team POSSESSION: 1 on the frames of the team's possession, where a member holds the ball, or
     * nobody does and the closest holders before and after are both members. Negated, 1 on every
     * other frame. Frame 0, the dummy frame, is 0.
     */
    private static int[] teamPossessionWord(int[] possession, int[] ids, boolean isNegated, int n) {
        Set<Integer> members = new HashSet<>();
        for (int id : ids) {
            if (id != 0) members.add(id); // 0 is nobody
        }
        int[] next = new int[n + 2]; // next[f]: the first holder from frame f on, 0 if none
        for (int f = n; f >= 1; f--) next[f] = possession[f] != 0 ? possession[f] : next[f + 1];
        int[] word = new int[n + 1];
        int previous = 0; // the last holder before frame f, 0 if none
        for (int f = 1; f <= n; f++) {
            boolean team = possession[f] != 0
                    ? members.contains(possession[f])
                    : members.contains(previous) && members.contains(next[f]);
            if (possession[f] != 0) previous = possession[f];
            word[f] = team != isNegated ? 1 : 0;
        }
        return word;
    }

    /**
     * Team MOVE_TO: 1 on the frames where every member of {@code group} is in zone {@code a}, 2
     * where every one is in zone {@code b}, 0 elsewhere (frame 0 included).
     */
    private static int[] groupWord(int[] group, int a, int b, int[][] positionZones, int n) {
        int[] word = new int[n + 1];
        for (int f = 1; f <= n; f++) {
            boolean allA = true, allB = true;
            for (int id : group) {
                int z = zone(positionZones, id, f);
                allA &= z == a;
                allB &= z == b;
            }
            word[f] = allA ? 1 : allB ? 2 : 0;
        }
        return word;
    }

    /** k slots of a team, and the players they stand for: {@code players[i]} is the player of {@code slots[i]}. */
    private record Group(int[] slots, int[] players) {}

    /**
     * The groups of k members that can make the move from zone a to zone b: k slots of the team and
     * distinct players for the wildcards among them, all in zone a on some frame and all in zone b
     * on a later one. Groups that differ only in how anonymous members share the same players are
     * one group.
     */
    private static List<Group> movingGroups(TeamMembers members, int k, int a, int b, int[][] positionZones, int n) {
        int[] template = members.template();
        Set<Integer> fixedIds = Arrays.stream(template).filter(id -> id >= 0).boxed().collect(Collectors.toSet());

        // frame by frame: the fixed members in zone a, and the other players there
        Set<List<List<Integer>>> inA = new LinkedHashSet<>();
        for (int f = 1; f <= n; f++) {
            List<Integer> fixedSlots = new ArrayList<>(), others = new ArrayList<>();
            for (int s = 0; s < template.length; s++) {
                if (template[s] >= 0 && zone(positionZones, template[s], f) == a) fixedSlots.add(s);
            }
            for (int id = 1; id < positionZones.length; id++) {
                if (!fixedIds.contains(id) && zone(positionZones, id, f) == a) others.add(id);
            }
            if (fixedSlots.size() + Math.min(members.wildcardSlots().length, others.size()) >= k) {
                inA.add(List.of(fixedSlots, others));
            }
        }

        Set<List<Integer>> tried = new HashSet<>();
        Set<String> kept = new HashSet<>();
        List<Group> groups = new ArrayList<>();
        for (List<List<Integer>> here : inA) {
            List<Integer> others = here.get(1);
            // the slots that may be in zone a on such a frame: its fixed members there, and the wildcards
            int[] slotsHere = IntStream.range(0, template.length)
                    .filter(s -> template[s] < 0 ? !others.isEmpty() : here.get(0).contains(s))
                    .toArray();
            for (int[] slots : subsets(slotsHere, k)) {
                fillGroup(members, slots, 0, new int[k], others, players -> {
                    List<Integer> attempt = new ArrayList<>();
                    for (int i = 0; i < k; i++) {
                        attempt.add(slots[i]);
                        attempt.add(players[i]);
                    }
                    if (!tried.add(attempt)) return;
                    if (tried.size() > MAX_TEAM_GROUPS_TRIED) {
                        throw new IllegalArgumentException("A team move has more than " + MAX_TEAM_GROUPS_TRIED
                                + " groups of " + k + " members to examine. Use a smaller team or a smaller k.");
                    }
                    if (appearsBefore(groupWord(players, a, b, positionZones, n), 1, 2)
                            && kept.add(teamKey(members, slots, players))) {
                        groups.add(new Group(slots.clone(), players.clone()));
                        checkCandidateCount(groups.size(), "groups of " + k + " members that make the move");
                    }
                });
            }
        }
        return groups;
    }

    /** Gives the slots their players: a fixed member its own, a wildcard one of {@code others} it may stand for. */
    private static void fillGroup(TeamMembers members, int[] slots, int i, int[] players, List<Integer> others,
                                  java.util.function.Consumer<int[]> sink) {
        if (i == slots.length) {
            sink.accept(players);
            return;
        }
        int w = members.wildcardAt(slots[i]);
        if (w < 0) {
            players[i] = members.template()[slots[i]];
            fillGroup(members, slots, i + 1, players, others, sink);
            return;
        }
        for (int v : others) {
            if (Arrays.binarySearch(members.candidates()[w], v) < 0) continue;
            boolean taken = false;
            for (int j = 0; j < i; j++) taken |= players[j] == v;
            if (taken) continue;
            players[i] = v;
            fillGroup(members, slots, i + 1, players, others, sink);
        }
    }

    /** The subsets of {@code k} elements of {@code items}, each in the order of {@code items}. */
    private static List<int[]> subsets(int[] items, int k) {
        List<int[]> out = new ArrayList<>();
        addSubsets(items, k, 0, new int[k], 0, out);
        return out;
    }

    private static void addSubsets(int[] items, int k, int from, int[] current, int size, List<int[]> out) {
        if (size == k) {
            out.add(current.clone());
            return;
        }
        for (int i = from; i <= items.length - (k - size); i++) {
            current[size] = items[i];
            addSubsets(items, k, i + 1, current, size + 1, out);
        }
    }

    /** Whether a frame with symbol {@code a} comes before a frame with symbol {@code b}. */
    private static boolean appearsBefore(int[] word, int a, int b) {
        boolean seenA = false;
        for (int symbol : word) {
            if (symbol == a) seenA = true;
            else if (symbol == b && seenA) return true;
        }
        return false;
    }

    // ###############################
    // CANDIDATES OF THE PLAYER EVENTS
    // ###############################

    /**
     * The key of a player in the identity map: its ID when the query gives one, so that
     * two names given the same ID are one player, and its name otherwise.
     */
    private static String identityKey(Player player) {
        return player.id() != null ? "#" + player.id() : player.name();
    }

    /**
     * The identity of the player of a POSITION or MOVE_TO event, shared with every event that names
     * it, restricted to the given ID, or to the players of the given side.
     */
    private CPIntVar playerIdentity(CPSolver cp, Player player, int[] teams, Map<String, CPIntVar> IDENTIFIERS,
                                    CPIntervalVar eventInterval) {
        CPIntVar player_id = IDENTIFIERS.get(identityKey(player));
        if (player_id == null) {
            player_id = makeIntVar(cp, teams.length);
            rememberIdentity(IDENTIFIERS, identityKey(player), player_id, eventInterval);
        } else {
            noteIdentityUse(identityKey(player), eventInterval);
        }
        if (player.id() != null) {
            cp.post(eq(player_id, player.id()));
        } else {
            for (int i = 0; i < teams.length; i++) {
                if (!onSide(player, teams[i])) player_id.remove(i);
            }
        }
        return player_id;
    }

    /** Whether a player of side {@code team} (0 left, 1 right) may be {@code player}. */
    private static boolean onSide(Player player, int team) {
        return player.team() == null ? team == 0 || team == 1 : team == (player.team().equals("left") ? 0 : 1);
    }

    /** Counts, in {@code uses}, every time {@code event} names a player, team members included. */
    private static void countIdentityUses(Event event, Map<String, Integer> uses) {
        if (event instanceof AndEvent and) {
            for (Event child : and.children()) countIdentityUses(child, uses);
        } else if (event instanceof OrEvent or) {
            for (Event child : or.children()) countIdentityUses(child, uses);
        } else {
            List<Player> players = new ArrayList<>();
            if (event.subject() instanceof Player p) players.add(p);
            if (event.subject() instanceof Team t) players.addAll(t.players());
            if (event.action().payload instanceof Player p) players.add(p);
            for (Player p : players) uses.merge(identityKey(p), 1, Integer::sum);
        }
    }

    /** The players {@code player_id} may still stand for, in increasing order; 0 is nobody. */
    private static int[] candidatesOf(CPIntVar player_id) {
        int[] values = new int[player_id.size()];
        player_id.fillArray(values);
        return Arrays.stream(values).filter(v -> v != 0).sorted().toArray();
    }

    /** Ties {@code player_id} to the player of the chosen candidate. */
    private void linkIdentity(CPSolver cp, CPIntervalVar eventInterval, CPIntVar player_id, int[] candidates,
                              CPIntVar chosen) {
        postWhenPresent(cp, eventInterval, isEq(player_id, selected(cp, candidates, chosen)));
    }

    /**
     * Posts that {@code window} is a move read on {@code word} (1: in the start zone, 2: in the end
     * zone, 0: anything else): from the last frame in the start zone to the first one in the end
     * zone with MINRANGE, from the first frame of that stay in the start zone to the last frame of
     * that stay in the end zone otherwise.
     */
    private void postMove(CPSolver cp, CPIntervalVar window, int[] word, boolean minrange, int n) {
        if (minrange) {
            cp.post(new SubstringRegular(word, window, Automaton.A_NOTBSTAR_B(2, 1, 2)));
        } else {
            postPadded(cp, window, word, Automaton.PAD_APLUS_NOTABSTAR_BPLUS_PAD(2, 1, 2), n);
        }
    }

    /**
     * POSITION of a player or of the ball: 1 on the frames where {@code id} is in the zones
     * (negated: is not, untracked frames included). Frame 0, the dummy frame, is 0.
     */
    private static int[] zoneWord(int[][] positionZones, int id, Set<Integer> zones, boolean isNegated, int n) {
        int[] word = new int[n + 1];
        for (int f = 1; f <= n; f++) word[f] = zones.contains(zone(positionZones, id, f)) != isNegated ? 1 : 0;
        return word;
    }

    /**
     * The possession sequence read by the events: a player keeps the ball from one touch to the
     * next as long as nobody else touches it, so the ball-free frames between two touches by the
     * same player belong to that player. Frame 0, the dummy frame, stays 0.
     */
    static int[] bridgeSamePlayer(int[] possession) {
        int[] bridged = possession.clone();
        int last = 0; // the frame of the last touch, 0 if none yet
        for (int f = 1; f < possession.length; f++) {
            if (possession[f] == 0) continue;
            if (last > 0 && possession[last] == possession[f]) {
                for (int g = last + 1; g < f; g++) bridged[g] = possession[f];
            }
            last = f;
        }
        return bridged;
    }

    private List<Result> order(List<Result> results) {
        Map<String, List<Result>> byInstance = new LinkedHashMap<>();

        // preserve the original instance order
        for (Result res : results) {
            byInstance.computeIfAbsent(res.instance, k -> new ArrayList<>()).add(res);
        }

        List<Result> ordered = new ArrayList<>(results.size());

        for (Map.Entry<String, List<Result>> entry : byInstance.entrySet()) {
            List<Result> group = new ArrayList<>(entry.getValue());

            group.sort(this::compareResultsLexicographicallyByTime);

            // renumber after sorting so "solution #..." matches printed order
            for (int i = 0; i < group.size(); i++) {
                Result old = group.get(i);
                ordered.add(new Result(i, old.events, old.instance));
            }
        }

        return ordered;
    }

    private int compareResultsLexicographicallyByTime(Result a, Result b) {
        int n = Math.min(a.events.size(), b.events.size());

        for (int i = 0; i < n; i++) {
            int c = compareEventsLexicographicallyByTime(a.events.get(i), b.events.get(i));
            if (c != 0) return c;
        }

        // if one result has fewer top-level events, it comes first
        int c = Integer.compare(a.events.size(), b.events.size());
        if (c != 0) return c;

        // stable fallback
        return Integer.compare(a.solutionIndex, b.solutionIndex);
    }

    private int compareEventsLexicographicallyByTime(Result.ResultEvent e1, Result.ResultEvent e2) {
        int c = Integer.compare(e1.interval.start, e2.interval.start);
        if (c != 0) return c;

        c = Integer.compare(e1.interval.end, e2.interval.end);
        if (c != 0) return c;

        // refine group events recursively
        if (e1 instanceof Result.GroupEvent g1 && e2 instanceof Result.GroupEvent g2) {
            int n = Math.min(g1.children.size(), g2.children.size());
            for (int i = 0; i < n; i++) {
                c = compareEventsLexicographicallyByTime(g1.children.get(i), g2.children.get(i));
                if (c != 0) return c;
            }

            c = Integer.compare(g1.children.size(), g2.children.size());
            if (c != 0) return c;
        }

        // deterministic fallback
        return Integer.compare(e1.eventIdx, e2.eventIdx);
    }


    public Result parseSolution(int solutionIndex, List<ExtendedCPVar> extVars, String instance) {
        AtomicInteger i = new AtomicInteger(0);
        List<Result.ResultEvent> events = new ArrayList<>();
        while (i.get() < extVars.size()) {
            Result.ResultEvent e = parseEvent(extVars, i);
            if (e != null) events.add(e);
        }
        return new Result(solutionIndex, events, instance);
    }

    /** Parses the event at the cursor: an atomic event, or a group with its members. */
    private Result.ResultEvent parseEvent(List<ExtendedCPVar> extVars, AtomicInteger i) {
        ExtendedCPVar ev = extVars.get(i.get());
        if (!"AND_interval".equals(ev.type) && !"OR_interval".equals(ev.type)) return parseSingleEvent(extVars, i);
        CPIntervalVar interval = (CPIntervalVar) ev.var;
        Result.GroupEvent ge = new Result.GroupEvent(ev.event_idx, ev.type.equals("AND_interval") ? "AND" : "OR", ev.subSz, new Result.ResultInterval(interval));
        i.incrementAndGet();
        for (int k = 0; k < ge.subSize && i.get() < extVars.size(); k++) {
            // every member is parsed so the cursor stays aligned, but an OR branch that was not
            // taken is absent: it belongs to the model, not to the match
            Result.ResultEvent child = parseEvent(extVars, i);
            if (child != null && child.interval.present) ge.children.add(child);
        }
        return ge;
    }

    private Result.ResultEvent parseSingleEvent(List<ExtendedCPVar> extVars, AtomicInteger i) {
        ExtendedCPVar ev = extVars.get(i.get());
        CPIntervalVar intervalVar = (CPIntervalVar) ev.var;
        Result.ResultInterval interval = new Result.ResultInterval(intervalVar);
        if ("PASS_TO".equals(ev.type)) {
            int eventIdx = ev.event_idx;
            boolean neg = ev.isNegated;
            i.incrementAndGet();
            int passer = ((CPIntVar) extVars.get(i.get()).var).min();
            i.incrementAndGet();
            int receiver = ((CPIntVar) extVars.get(i.get()).var).min();
            i.incrementAndGet();
            return new Result.PassEvent(eventIdx, neg, interval, passer, receiver);
        } else if ("POSSESSION".equals(ev.type)) {
            int eventIdx = ev.event_idx;
            boolean neg = ev.isNegated;
            List<Integer> players = new ArrayList<>();
            i.incrementAndGet(); // move to first player id if any
            while (i.get() < extVars.size() && extVars.get(i.get()).type.contains("_player_id")) {
                players.add(((CPIntVar) extVars.get(i.get()).var).min());
                i.incrementAndGet();
            }
            return new Result.PossessionEvent(eventIdx, neg, interval, players);
        } else if ("PLAYER_MOVE_TO".equals(ev.type)) {
            int eventIdx = ev.event_idx;
            boolean neg = ev.isNegated;
            i.incrementAndGet();
            int pid = ((CPIntVar) extVars.get(i.get()).var).min();
            i.incrementAndGet();
            return new Result.PlayerMoveEvent(eventIdx, neg, interval, pid);
        } else if ("TEAM_MOVE_TO".equals(ev.type)) {
            int eventIdx = ev.event_idx;
            boolean neg = ev.isNegated;
            List<Integer> players = new ArrayList<>();
            i.incrementAndGet();
            while (i.get() < extVars.size() && extVars.get(i.get()).type.contains("_player_id")) {
                players.add(((CPIntVar) extVars.get(i.get()).var).min());
                i.incrementAndGet();
            }
            return new Result.TeamMoveEvent(eventIdx, neg, interval, players);
        } else if ("POSITION".equals(ev.type)) {
            int eventIdx = ev.event_idx;
            boolean neg = ev.isNegated;
            List<Integer> players = new ArrayList<>();
            i.incrementAndGet();
            while (i.get() < extVars.size() && extVars.get(i.get()).type.contains("_player_id")) {
                players.add(((CPIntVar) extVars.get(i.get()).var).min());
                i.incrementAndGet();
            }
            return new Result.PositionEvent(eventIdx, neg, interval, players);
        } else if ("BALL_MOVE_TO".equals(ev.type)) {
            int eventIdx = ev.event_idx;
            boolean neg = ev.isNegated;
            i.incrementAndGet();
            return new Result.BallMoveEvent(eventIdx, neg, interval);
        } else {
            i.incrementAndGet();
            return null;
        }
    }

    public static class ExtendedCPVar {
        CPVar var;
        int order;
        String type;
        int event_idx;
        boolean isNegated;
        int subSz;

        public ExtendedCPVar(CPVar var, int order, String type, int event_idx, boolean isNeg) {
            this.event_idx = event_idx;
            this.var = var;
            this.order = order;
            this.type = type;
            this.isNegated = isNeg;
            this.subSz = 0;
        }

        public ExtendedCPVar(CPVar var, int order, String type, int event_idx, boolean isNeg, int subSz) {
            this.event_idx = event_idx;
            this.var = var;
            this.order = order;
            this.type = type;
            this.isNegated = isNeg;
            this.subSz = subSz;
        }
    }
}


