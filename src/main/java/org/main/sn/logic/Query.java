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
import java.util.stream.Stream;

import static org.maxicp.cp.CPFactory.*;

public class Query {

    private static final int ABSENT = 1;

    public static final String MAX_SOLUTIONS_PROPERTY = "sn.maxSolutions";
    private static final int DEFAULT_MAX_SOLUTIONS = 5_000_000;

    public static int maxSolutions() {
        return Integer.getInteger(MAX_SOLUTIONS_PROPERTY, DEFAULT_MAX_SOLUTIONS);
    }

    private final Map<CPIntervalVar, CPIntVar> startVars = new IdentityHashMap<>();
    private final Map<CPIntervalVar, CPIntVar> endVars = new IdentityHashMap<>();

    private final Set<CPIntervalVar> mayBeAbsent = Collections.newSetFromMap(new IdentityHashMap<>());

    public List<Result> apply(SelectExpr seq) {


        List<Result> r = new ArrayList<>();

        final List<Event> steps = seq.steps;
        final List<GameStateReconstructionInstance> matches = seq.matches;
        int total_max_duration = seq.maxDuration;
        int total_min_duration = seq.minDuration;
        int total_start_min = seq.startMin;
        int total_start_max = seq.startMax;
        int total_end_min = seq.endMin;
        int total_end_max = seq.endMax;
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

            CPSolver cp = makeSolver();

            Map<String, CPIntVar> IDENTIFIERS = new HashMap<>(); //Entity's name -> CP variable

            ArrayList<ExtendedCPVar> extVars = new ArrayList<>();
            ArrayList<CPIntervalVar> intervals = new ArrayList<>();
            ArrayList<CPIntervalVar> topIntervals = new ArrayList<>();
            AtomicInteger counterVars = new AtomicInteger(-1);
            AtomicInteger counterEvent = new AtomicInteger(0);

            startVars.clear();
            endVars.clear();
            identityOwner.clear();
            mayBeAbsent.clear();
            alwaysPresentIdentities.clear();

            try {
                int[] teams = instance.teams;
                int n = instance.n;
                int ball_idx = instance.ball_idx;
                int nbZones = instance.nbZones;
                Possession p = new Possession(cp, instance, true);
                int[] possession = p.result;
                Position pos = new Position(cp, instance);
                int[][] positionZones = pos.position;

                int[][] positionBox_x = new int[teams.length][n];
                int[][] positionBox_y = new int[teams.length][n];

                Map<Integer, GameStateReconstructionInstance.FrameData> frameData = instance.positions;

                for (Event event : steps) {
                    if (event instanceof AndEvent andEvent) {
                        intervals.add(makeIntervalVar(cp, false, 0, n + 1)); //the whole AndEvent
                        topIntervals.add(intervals.getLast());
                        for (Event child : andEvent.children()) {
                            intervals.add(makeIntervalVar(cp, false, 0, n + 1));
                        }
                    } else if (event instanceof OrEvent orEvent) {
                        intervals.add(makeIntervalVar(cp, false,0, n + 1)); //the whole OrEvent
                        topIntervals.add(intervals.getLast());
                        for (Event child : orEvent.children()) {
                            intervals.add(makeOptionalInterval(cp, 0, n + 1));
                        }
                    } else {
                        intervals.add(makeIntervalVar(cp, false, 0, n + 1));
                        topIntervals.add(intervals.getLast());
                    }
                }

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
                    CPIntervalVar event_interval = intervals.get(counterEvent.get());
                    CPIntVar event_start = startOf(event_interval);
                    CPIntVar event_end = endOf(event_interval);
                    cp.post(gt(minus(event_end, 1), event_start)); //because end non inclusive

                    modelEvent(event, cp, counterEvent, event_interval, event_start, event_end, prevTopInterval, ball_idx, n, positionZones, positionBox_x,
                            positionBox_y, total_circle, extVars, intervals, total_rectangle, counterVars, teams,
                            IDENTIFIERS, total_xcenter, possession, total_ycenter, total_radius, total_xtop, total_ytop,
                            total_w, total_h, nbZones);
                    prevTopInterval = event_interval;
                }

                //AllDifferent on playersID
                List<CPIntVar> alwaysPresent = new ArrayList<>();
                List<String> branchLocal = new ArrayList<>();
                for (Map.Entry<String, CPIntVar> identity : IDENTIFIERS.entrySet()) {
                    CPIntervalVar owner = identityOwner.get(identity.getKey());
                    if (owner != null && mayBeAbsent.contains(owner) && !alwaysPresentIdentities.contains(identity.getKey())) branchLocal.add(identity.getKey());
                    else alwaysPresent.add(identity.getValue());
                }

                if (!alwaysPresent.isEmpty()) cp.post(allDifferent(alwaysPresent.toArray(CPIntVar[]::new)));

                for (int a = 0; a < branchLocal.size(); a++) {
                    CPIntVar va = IDENTIFIERS.get(branchLocal.get(a));
                    CPIntervalVar oa = identityOwner.get(branchLocal.get(a));
                    for (CPIntVar vb : alwaysPresent) {
                        postDistinctWhenBothPresent(cp, oa, null, va, vb);
                    }
                    for (int b = a + 1; b < branchLocal.size(); b++) {
                        postDistinctWhenBothPresent(cp, oa, identityOwner.get(branchLocal.get(b)),
                                va, IDENTIFIERS.get(branchLocal.get(b)));
                    }
                }

                for (String name : branchLocal) {
                    CPIntVar identity = IDENTIFIERS.get(name);
                    cp.post(implies(not(identityOwner.get(name).status()), isEq(identity, identity.min())));
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
                continue;
            }

            // ******
            // SEARCH
            // ******

            DFSearch search = makeDfs(cp, Searches.firstFailBinary(
                    Stream.concat(
                            IDENTIFIERS.values().stream(),
                            intervals.stream().flatMap(itv -> mayBeAbsent.contains(itv)
                                    ? Stream.<CPIntVar>of(itv.status(), startOf(itv), endOf(itv))
                                    : Stream.<CPIntVar>of(startOf(itv), endOf(itv)))
                    ).toArray(CPIntVar[]::new)));
            AtomicInteger solutionCounter = new AtomicInteger(0);

            search.onSolution(() -> {
                int solnb = solutionCounter.getAndIncrement();
                r.add(parseSolution(solnb, extVars, instance.name));
            });

            // Stop predicate for the interval-count guard: solve(...) halts as soon as
            // this returns true. A cap of 0 or less means "no guard".
            final int solutionCap = maxSolutions();
            Predicate<SearchStatistics> capReached = solutionCap > 0
                    ? statistics -> statistics.numberOfSolutions() >= solutionCap
                    : statistics -> false;

            if (searchMode != -1 || atMost != -1) {
                if (searchMode == 0) search.solve(capReached);
                else if (searchMode >= 1)
                    search.solve(statistics -> statistics.numberOfSolutions() == searchMode || capReached.test(statistics));
                else if (atMost >= 0)
                    search.solve(statistics -> statistics.numberOfSolutions() <= atMost || capReached.test(statistics));
            } else {
                search.solve(capReached);
            }

            if (solutionCap > 0 && solutionCounter.get() >= solutionCap) {
                throw new IllegalStateException(
                        "Query produced more than " + solutionCap + " matches on " + instance.name
                                + " and was abandoned. Narrow it — add a WHERE(MAXDURATION(...)) or a "
                                + "tighter zone/entity, or avoid an OR whose branches hold for most of the "
                                + "clip — or raise the cap with -D" + MAX_SOLUTIONS_PROPERTY + "=<n> "
                                + "(0 disables it).");
            }

            if (atLeast >= 0) {
                if (solutionCounter.get() < atLeast) {
                    //System.out.println("Number of solutions found (" + solutionCounter.get() + ") is less than the specified atLeast value (" + atLeast + ").");
                    r.subList(r.size() - solutionCounter.get(), r.size()).clear();
                }
            }
        }


        return order(r);
    }


    // ##################################
    // HELPER METHODS FOR MODELING EVENTS
    // ##################################


    private void modelEvent(Event event, CPSolver cp, AtomicInteger counterEvent, CPIntervalVar event_interval, CPIntVar event_start, CPIntVar event_end,
                            CPIntervalVar prevTopInterval,
                            int ball_idx, int n, int[][] positionZones, int[][] positionBox_x,
                            int[][] positionBox_y, boolean total_circle, ArrayList<ExtendedCPVar> extVars, ArrayList<CPIntervalVar> intervals,
                            boolean total_rectangle, AtomicInteger counterVars, int[] teams,
                            Map<String, CPIntVar> IDENTIFIERS, int total_xcenter, int[] possession,
                            int total_ycenter, int total_radius, int total_xtop, int total_ytop,
                            int total_w, int total_h, int nbZones) {


        if (event instanceof AndEvent andEvent) {

            extVars.add(new ExtendedCPVar(
                    event_interval,
                    counterVars.incrementAndGet(),
                    "AND_interval",
                    counterEvent.get(),
                    andEvent.isNegated,
                    andEvent.children().size()));

            counterEvent.incrementAndGet();

            CPIntVar[] childrenStarts = new CPIntVar[andEvent.children().size()];
            CPIntVar[] childrenEnds = new CPIntVar[andEvent.children().size()];

            int childIdx = 0;

            for (Event children : andEvent.children()) {
                CPIntervalVar childInterval = intervals.get(counterEvent.get());
                childrenStarts[childIdx] = startOf(childInterval);
                childrenStarts[childIdx].removeAbove(n + 1);
                childrenEnds[childIdx] = endOf(childInterval);
                childrenEnds[childIdx].removeAbove(n + 1);

                executeStep(children, cp, counterEvent, childInterval, childrenStarts[childIdx], childrenEnds[childIdx], null, ball_idx, n,
                        positionZones, positionBox_x, positionBox_y, total_circle, extVars, intervals, total_rectangle,
                        counterVars, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                        total_radius, total_xtop, total_ytop, total_w, total_h, true, childIdx, andEvent.children().size(), nbZones);

                childIdx++;
            }

            //define the interval of the AND event
            cp.post(eq(event_start, max(childrenStarts)));
            cp.post(eq(event_end, minimum(childrenEnds)));

        } else if (event instanceof OrEvent orEvent) { // or should return the union of intervals

            //we use frame_start and frame_end to represent the AND event interval
            extVars.add(new ExtendedCPVar(
                    event_interval,
                    counterVars.incrementAndGet(),
                    "OR_interval",
                    counterEvent.get(),
                    orEvent.isNegated,
                    orEvent.children().size()));

            counterEvent.incrementAndGet();

            CPIntVar[] childrenStarts = new CPIntVar[orEvent.children().size()];
            CPIntVar[] childrenEnds = new CPIntVar[orEvent.children().size()];

            int childIdx = 0;
            CPBoolVar[] statuses = new CPBoolVar[orEvent.children().size()];
            for (Event children : orEvent.children()) {
                CPIntervalVar childInterval = intervals.get(counterEvent.get());
                childrenStarts[childIdx] = startOr(childInterval, n+1);
                childrenStarts[childIdx].removeAbove(n + 1);
                childrenEnds[childIdx] = endOr(childInterval, 0);
                childrenEnds[childIdx].removeAbove(n + 1);
                statuses[childIdx] = childInterval.status();

                // a branch that cannot hold in this game is switched off, not fatal.
                int childEventIdx = counterEvent.get();
                try {
                    executeStep(children, cp, counterEvent, childInterval, startOf(childInterval), endOf(childInterval), null, ball_idx, n,
                            positionZones, positionBox_x, positionBox_y, total_circle, extVars, intervals, total_rectangle,
                            counterVars, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                            total_radius, total_xtop, total_ytop, total_w, total_h, true, childIdx, orEvent.children().size(), nbZones);
                } catch (InconsistencyException e) {
                    childInterval.setAbsent();
                    counterEvent.set(childEventIdx + 1); //executeStep did not reach its own increment
                }

                childIdx++;
            }

            cp.post(eq(sum(statuses), 1));

            cp.post(eq(event_start, minimum(childrenStarts)));
            cp.post(eq(event_end, max(childrenEnds)));

        } else {
            executeStep(event, cp, counterEvent, event_interval, event_start, event_end, prevTopInterval, ball_idx, n,
                    positionZones, positionBox_x, positionBox_y, total_circle, extVars, intervals, total_rectangle,
                    counterVars, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                    total_radius, total_xtop, total_ytop, total_w, total_h, false, 0, 0, nbZones);
        }
    }


    private void executeStep(Event event, CPSolver cp, AtomicInteger counterEvent, CPIntervalVar eventInterval, CPIntVar event_start, CPIntVar event_end,
                             CPIntervalVar prevTopInterval,
                             int ball_idx, int n, int[][] positionZones, int[][] positionBox_x,
                             int[][] positionBox_y, boolean total_circle, ArrayList<ExtendedCPVar> extVars, ArrayList<CPIntervalVar> intervals,
                             boolean total_rectangle, AtomicInteger counterVars,
                             int[] teams, Map<String, CPIntVar> IDENTIFIERS, int total_xcenter, int[] possession,
                             int total_ycenter, int total_radius, int total_xtop, int total_ytop,
                             int total_w, int total_h, boolean isAndEvent, int childIdx, int nbChild, int nbZones) {

        Action action = event.action();
        Entity subject = event.subject();
        int event_timeStartMin = event.timeStartMin;
        int event_timeStartMax = event.timeStartMax;
        int event_timeEndMin = event.timeEndMin;
        int event_timeEndMax = event.timeEndMax;
        int event_durationMax = event.maxDuration;
        int event_durationMin = event.minDuration;
        int event_radius = event.radius;
        int event_xcenter = event.xcenter;
        int event_ycenter = event.ycenter;
        int event_xtop = event.xtop;
        int event_ytop = event.ytop;
        int event_w = event.w;
        int event_h = event.h;
        boolean minrange = event.isMinrange;
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

        if (prevTopInterval != null && !isAndEvent) {
            cp.post(endBeforeStart(prevTopInterval, eventInterval, 1));
        }

        if (event_timeStartMin != -1) {
            eventInterval.setStartMin(event_timeStartMin);
        }
        if (event_timeStartMax != -1) {
            eventInterval.setStartMax(event_timeStartMax);
        }
        if (event_timeEndMin != -1) {
            eventInterval.setEndMin(event_timeEndMin + 1); //end is exclusive so > k means "last frame >= k"
        }
        if (event_timeEndMax != -1) {
            eventInterval.setEndMax(event_timeEndMax + 1);
        }
        if (event_durationMax != -1) {
            eventInterval.setLengthMax(event_durationMax);
        }
        if (event_durationMin != -1) {
            eventInterval.setLengthMin(event_durationMin);
        }

        switch (action.name) {

            //BALL EVENTS

            case "BALL_MOVE_TO" -> model_BALL_MOVE_TO(cp, isNegated, payload, eventInterval, ball_idx, n,
                    positionZones, positionBox_x, positionBox_y, event_circle, event_rectangle,
                    total_circle, total_rectangle, event_xcenter, event_ycenter, event_radius,
                    event_xtop, event_ytop, event_w, event_h, total_xcenter, total_ycenter,
                    total_radius, total_xtop, total_ytop, teams, total_w, total_h, minrange);

            //PLAYER EVENTS

            case "PASS_TO" ->
                    model_PASS_TO(cp, counterEvent, isNegated, payload, eventInterval, ball_idx, n, possession,
                            positionBox_x, positionBox_y, event_circle, event_rectangle, total_circle,
                            total_rectangle, event_xcenter, event_ycenter, event_radius, event_xtop,
                            event_ytop, event_w, event_h, total_xcenter, total_ycenter, total_radius,
                            total_xtop, total_ytop, total_w, total_h, subject, teams,
                            IDENTIFIERS, counterVars, action, extVars, minrange);

            case "PLAYER_MOVE_TO" -> model_PLAYER_MOVE_TO(cp, counterEvent, isNegated, eventInterval, event_circle,
                    event_rectangle, total_circle, total_rectangle, event_xcenter, event_ycenter,
                    event_radius, event_xtop, event_ytop, event_w, event_h, total_xcenter,
                    total_ycenter, total_radius, total_xtop, total_ytop, total_w, total_h,
                    subject, teams, positionBox_x, positionBox_y, positionZones, payload, n, IDENTIFIERS,
                    counterVars, action, extVars, minrange);

            //TEAM EVENTS

            case "TEAM_MOVE_TO" ->
                    model_TEAM_MOVE_TO(cp, counterEvent, isNegated, eventInterval, event_start, event_end, event_circle,
                            event_rectangle, total_circle, total_rectangle, event_xcenter, event_ycenter,
                            event_radius, event_xtop, event_ytop, event_w, event_h, total_xcenter,
                            total_ycenter, total_radius, total_xtop, total_ytop, total_w, total_h,
                            subject, teams, positionBox_x, positionBox_y, positionZones, payload, n, IDENTIFIERS,
                            counterVars, action, extVars, minrange);

            //TEAM + PLAYER EVENTS

            case "POSSESSION" ->
                    model_POSSESSION(cp, counterEvent, isNegated, eventInterval, event_start, event_end, ball_idx, n,
                            possession, event_circle, event_rectangle, total_circle, total_rectangle,
                            event_xcenter, event_ycenter, event_radius, event_xtop, event_ytop, event_w,
                            event_h, total_xcenter, total_ycenter, total_radius, total_xtop, total_ytop,
                            total_w, total_h, subject, IDENTIFIERS, counterVars, action, extVars,
                            teams, positionBox_x, positionBox_y);

            // TEAM + PLAYER + BALL EVENTS

            case "POSITION" ->
                    model_POSITION(cp, counterEvent, isNegated, eventInterval, event_start, event_end, event_circle,
                            event_rectangle, total_circle, total_rectangle, event_xcenter, event_ycenter, event_radius,
                            event_xtop, event_ytop, event_w, event_h, total_xcenter, total_ycenter, total_radius, total_xtop,
                            total_ytop, total_w, total_h, subject, teams, positionBox_x, positionBox_y, positionZones,
                            payload, n, IDENTIFIERS, counterVars, action, extVars, ball_idx, nbZones);

        }
        counterEvent.incrementAndGet();
    }

    private void
    model_BALL_MOVE_TO(CPSolver cp, boolean isNegated, Object payload, CPIntervalVar eventInterval,
                       int ball_idx, int n, int[][] positionZones, int[][] positionBox_x,
                       int[][] positionBox_y, boolean event_circle, boolean event_rectangle, boolean total_circle,
                       boolean total_rectangle, int event_xcenter, int event_ycenter, int event_radius,
                       int event_xtop, int event_ytop, int event_w, int event_h, int total_xcenter,
                       int total_ycenter, int total_radius, int total_xtop, int total_ytop, int[] teams,
                       int total_w, int total_h, boolean minrange) {

        //ball movement logic

        int zone_start = ((int[]) payload)[0];
        int zone_end = ((int[]) payload)[1];
        if (zone_start == zone_end) {
            throw new IllegalArgumentException("Cannot move from one zone to the same zone.");
        }

        if (isNegated) {
            throw new IllegalArgumentException("Negation of BALL_MOVE_TO is not supported.");
        } else {
            if (minrange) {
                cp.post(new SubstringRegular(positionZones[ball_idx], eventInterval, Automaton.A_NOTBSTAR_B(teams.length, zone_start, zone_end)));
            } else {
                CPIntervalVar paddedInterval = paddedFor(cp, eventInterval, n + 1);
                int[] paddedArray = Automaton.pad(positionZones[ball_idx]);
                cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.PAD_APLUS_PADSTAR_BPLUS_PAD(teams.length, zone_start, zone_end)));
                linkPadded(cp, eventInterval, paddedInterval);
            }
        }

        // spatial constraints

        if (event_circle || event_rectangle || total_circle || total_rectangle) {
            if (event_circle || total_circle) {
                List<double[]> circles = new ArrayList<>();
                if (event_circle)
                    circles.add(new double[]{Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                if (total_circle)
                    circles.add(new double[]{Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                for (double[] circle : circles) {
                    int[] ball_inside_circle = new int[n]; //true = 1, false = 0
                    for (int f = 0; f < n; f++) {
                        double dist_x = Math.pow(positionBox_x[ball_idx][f] - circle[1], 2);
                        double dist_y = Math.pow(positionBox_y[ball_idx][f] - circle[2], 2);

                        ball_inside_circle[f] = dist_x + dist_y <= circle[0] ? 1 : 0;
                    }
                    cp.post(new SubstringRegular(ball_inside_circle, eventInterval, Automaton.APLUS(1, Set.of(1))));
                }
            } if (event_rectangle || total_rectangle) {
                List<double[]> rectangles = new ArrayList<>();
                if (event_rectangle)
                    rectangles.add(new double[]{event_xtop, event_ytop, event_w, event_h});
                if (total_rectangle)
                    rectangles.add(new double[]{total_xtop, total_ytop, total_w, total_h});

                for (double[] rect : rectangles) {
                    int[] ball_inside_rectangle = new int[n];

                    for (int f = 0; f < n; f++) {
                        double bx = positionBox_x[ball_idx][f];
                        double by = positionBox_y[ball_idx][f];
                        ball_inside_rectangle[f] = (bx >= rect[0] && bx <= rect[0] + rect[2] && by <= rect[1] && by >= rect[1] - rect[3]) ? 1 : 0;
                    }
                    cp.post(new SubstringRegular(ball_inside_rectangle, eventInterval, Automaton.APLUS(1, Set.of(1))));
                }
            }
        }
    }

    public void model_PASS_TO(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, Object payload, CPIntervalVar eventInterval,
                              int ball_idx, int n, int[] possession, int[][] positionBox_x, int[][] positionBox_y,
                              boolean event_circle, boolean event_rectangle, boolean total_circle, boolean total_rectangle,
                              int event_xcenter, int event_ycenter, int event_radius, int event_xtop, int event_ytop,
                              int event_w, int event_h, int total_xcenter, int total_ycenter, int total_radius,
                              int total_xtop, int total_ytop, int total_w, int total_h,
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

        if (IDENTIFIERS.get(player_from.name()) == null) {
            if (!isNegated) passer_id = boundWhenPresent(cp, eventInterval, possessionAtStart, teams.length);
            rememberIdentity(IDENTIFIERS, player_from.name, passer_id, eventInterval);
        } else {
            passer_id = IDENTIFIERS.get(player_from.name);
            noteIdentityUse(player_from.name, eventInterval);
            if (!isNegated) postWhenPresent(cp, eventInterval, isEq(passer_id, possessionAtStart));

        }

        if (IDENTIFIERS.get(player_to.name()) == null) {
            if (!isNegated) receiver_id = boundWhenPresent(cp, eventInterval, possessionAtEnd, teams.length);
            rememberIdentity(IDENTIFIERS, player_to.name, receiver_id, eventInterval);
        } else {
            receiver_id = IDENTIFIERS.get(player_to.name);
            noteIdentityUse(player_to.name, eventInterval);
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


        if (isNegated) {
            throw new IllegalArgumentException("Negation of PASS_TO is not supported.");
        } else {
            if (minrange) {
                //cp.post(new SubstringRegular(possession, eventInterval, Automaton.A_0STAR_B(teams.length, passersIds, receiversIds)));
                cp.post(new SubstringRegular(possession, eventInterval, Automaton.A_0PLUS_B(teams.length, passersIds, receiversIds)));
            } else {
                CPIntervalVar paddedInterval = paddedFor(cp, eventInterval, n + 1);
                int[] paddedArray = Automaton.pad(possession);
                //cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.PAD_APLUS_PADSTAR_BPLUS_PAD(teams.length, passersIds, receiversIds)));
                cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.PAD_APLUS_0PLUS_BPLUS_PAD(teams.length, passersIds, receiversIds)));
                linkPadded(cp, eventInterval, paddedInterval);
            }
        }


        // spatial constraints
        if (event_circle || event_rectangle || total_circle || total_rectangle) {
            if (event_circle || total_circle) {
                List<double[]> circles = new ArrayList<>();
                if (event_circle)
                    circles.add(new double[]{Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                if (total_circle)
                    circles.add(new double[]{Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                for (double[] circle : circles) {
                    int[] ball_inside_circle = new int[n];
                    for (int f = 0; f < n; f++) {
                        double dist_x = Math.pow(positionBox_x[ball_idx][f] - circle[1], 2);
                        double dist_y = Math.pow(positionBox_y[ball_idx][f] - circle[2], 2);
                        ball_inside_circle[f] = dist_x + dist_y <= circle[0] ? 1 : 0;
                    }
                    cp.post(new SubstringRegular(ball_inside_circle, eventInterval, Automaton.APLUS(teams.length, Set.of(1))));
                }
            } if (event_rectangle || total_rectangle) {
                List<double[]> rectangles = new ArrayList<>();
                if (event_rectangle)
                    rectangles.add(new double[]{event_xtop, event_ytop, event_w, event_h});
                if (total_rectangle)
                    rectangles.add(new double[]{total_xtop, total_ytop, total_w, total_h});

                for (double[] rect : rectangles) {
                    int[] ball_inside_rectangle = new int[n];
                    for (int f = 0; f < n; f++) {
                        double bx = positionBox_x[ball_idx][f];
                        double by = positionBox_y[ball_idx][f];
                        ball_inside_rectangle[f] = (bx >= rect[0] && bx <= rect[0] + rect[2] && by <= rect[1] && by >= rect[1] - rect[3]) ? 1 : 0;
                    }
                    cp.post(new SubstringRegular(ball_inside_rectangle, eventInterval, Automaton.APLUS(teams.length, Set.of(1))));
                }
            }
        }
    }

    public void model_PLAYER_MOVE_TO(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntervalVar eventInterval, boolean event_circle,
                                     boolean event_rectangle, boolean total_circle, boolean total_rectangle, int event_xcenter,
                                     int event_ycenter, int event_radius, int event_xtop, int event_ytop,
                                     int event_w, int event_h, int total_xcenter, int total_ycenter, double total_radius,
                                     int total_xtop, int total_ytop, int total_w, int total_h,
                                     Entity subject, int[] teams, int[][] positionBox_x, int[][] positionBox_y,
                                     int[][] positionZones, Object payload, int n, Map<String, CPIntVar> IDENTIFIERS,
                                     AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, boolean minrange) {


        int zone_start = ((int[]) payload)[0];
        int zone_end = ((int[]) payload)[1];

        if (zone_start == zone_end) {
            throw new IllegalArgumentException("Cannot move from one zone to the same zone.");
        }

        Player player = (Player) subject;

        CPIntVar player_id = makeIntVar(cp, teams.length);

        if (IDENTIFIERS.get(player.name()) == null) {
            rememberIdentity(IDENTIFIERS, player.name, player_id, eventInterval);
        } else {
            player_id = IDENTIFIERS.get(player.name);
            noteIdentityUse(player.name, eventInterval);
        }


        ExtendedCPVar player_id_ext = new ExtendedCPVar(
                player_id,
                counterVars.incrementAndGet(),
                action.name + "_player_id",
                counterEvent.get(),
                isNegated
        );
        extVars.add(player_id_ext);

        if (player.id() != null) {
            cp.post(eq(player_id, player.id()));
        } else if (player.team() != null) {
            int team = player.team().equals("left") ? 0 : 1;
            for (int i = 0; i < teams.length; i++) {
                if (teams[i] != team) {
                    player_id.remove(i);
                }
            }
        } else {
            for (int i = 0; i < teams.length; i++) {
                if (teams[i] != 0 && teams[i] != 1) {
                    player_id.remove(i);
                }
            }
        }

        if (isNegated) {
            throw new IllegalArgumentException("Negation of PLAYER_MOVE_TO is not supported.");
        } else {
            int[] possible_players = new int[teams.length];
            player_id.fillArray(possible_players);
            CPBoolVar[] isThisPlayerVars = new CPBoolVar[teams.length];

            for (int pl_id : possible_players) {
                if (pl_id == 0) {
                    continue;
                }
                CPIntervalVar thisPlayerTrueInterval = makeIntervalVar(cp, false, 0, n + 1);
                try {
                    if (minrange) {
                        cp.post(new SubstringRegular(positionZones[pl_id], thisPlayerTrueInterval, Automaton.A_NOTBSTAR_B(teams.length, zone_start, zone_end)));
                    } else {
                        CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n + 1);
                        int[] paddedArray = Automaton.pad(positionZones[pl_id]);
                        cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.PAD_APLUS_PADSTAR_BPLUS_PAD(teams.length, zone_start, zone_end)));
                        cp.post(eq(start(thisPlayerTrueInterval), start(paddedInterval)));
                        cp.post(eq(end(thisPlayerTrueInterval), minus(end(paddedInterval), 2)));
                    }
                    //isThisPlayerVars[pl_id] = isEq(sum(isEq(player_id, pl_id), isEq(start(thisPlayerTrueInterval), start(eventInterval)), isEq(end(thisPlayerTrueInterval), end(eventInterval))), 3);
                    isThisPlayerVars[pl_id] = isEq(player_id, pl_id);
                    impliesWhenPresent(cp, eventInterval, isThisPlayerVars[pl_id],
                            isEq(start(thisPlayerTrueInterval), startOf(eventInterval)));
                    impliesWhenPresent(cp, eventInterval, isThisPlayerVars[pl_id],
                            isEq(end(thisPlayerTrueInterval), endOf(eventInterval)));

                } catch (InconsistencyException e) {
                    isThisPlayerVars[pl_id] = makeBoolVar(cp);
                    isThisPlayerVars[pl_id].fix(false);
                }
            }
            // exactly one candidate player realises the event -- but only if the event happens at all
            postWhenPresent(cp, eventInterval, isEq(sum(Arrays.stream(isThisPlayerVars)
                    .filter(Objects::nonNull)
                    .toArray(CPBoolVar[]::new)), 1));
        }

        // spatial constraints
        if (event_circle || event_rectangle || total_circle || total_rectangle) {

            //Transposed matrices
            int[][] positionBox_x_T = new int[n][teams.length];
            int[][] positionBox_y_T = new int[n][teams.length];
            for (int playerId = 0; playerId < teams.length; playerId++) {
                for (int frame = 0; frame < n; frame++) {
                    positionBox_x_T[frame][playerId] = positionBox_x[playerId][frame];
                }
            }
            for (int playerId = 0; playerId < teams.length; playerId++) {
                for (int frame = 0; frame < n; frame++) {
                    positionBox_y_T[frame][playerId] = positionBox_y[playerId][frame];
                }
            }

            if (event_circle || total_circle) {
                List<int[]> circles = new ArrayList<>();
                if (event_circle)
                    circles.add(new int[]{(int) Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                if (total_circle)
                    circles.add(new int[]{(int) Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                for (int[] circle : circles) {
                    CPBoolVar[] pl_inside_circle = new CPBoolVar[n]; //true = 1, false = 0
                    for (int f = 0; f < n; f++) {
                        int[] insideCircle = new int[teams.length];
                        for (int pid = 0; pid < teams.length; pid++) {
                            int dx = positionBox_x_T[f][pid] - circle[1];
                            int dy = positionBox_y_T[f][pid] - circle[2];
                            insideCircle[pid] = (dx * dx + dy * dy <= circle[0]) ? 1 : 0;
                        }
                        pl_inside_circle[f] = isEq(element(insideCircle, player_id), 1);
                    }
                    cp.post(new TrueInterval(pl_inside_circle, eventInterval));
                }
            } if (event_rectangle || total_rectangle) {
                List<int[]> rectangles = new ArrayList<>();
                if (event_rectangle)
                    rectangles.add(new int[]{event_xtop, event_ytop, event_w, event_h});
                if (total_rectangle)
                    rectangles.add(new int[]{total_xtop, total_ytop, total_w, total_h});

                for (int[] rect : rectangles) {
                    CPBoolVar[] pl_inside_rect = makeBoolVarArray(cp, n);
                    for (int f = 0; f < n; f++) {
                        CPIntVar bx = element(positionBox_x_T[f], player_id);
                        CPIntVar by = element(positionBox_y_T[f], player_id);
                        CPBoolVar upper_bound_x = isLe(bx, rect[0] + rect[2]);
                        CPBoolVar lower_bound_x = isGe(bx, rect[0]);
                        CPBoolVar upper_bound_y = isLe(by, rect[1]);
                        CPBoolVar lower_bound_y = isGe(by, rect[1] - rect[3]);
                        pl_inside_rect[f] = isEq(sum(upper_bound_x, lower_bound_x, upper_bound_y, lower_bound_y), 4); //inside the rectangle
                    }
                    cp.post(new TrueInterval(pl_inside_rect, eventInterval));
                }
            }
        }
    }

    public void model_TEAM_MOVE_TO(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntervalVar eventInterval, CPIntVar event_start, CPIntVar event_end,
                                   boolean event_circle, boolean event_rectangle, boolean total_circle, boolean total_rectangle, int event_xcenter,
                                   int event_ycenter, int event_radius, int event_xtop, int event_ytop,
                                   int event_w, int event_h, int total_xcenter, int total_ycenter, double total_radius,
                                   int total_xtop, int total_ytop, int total_w, int total_h,
                                   Entity subject, int[] teams, int[][] positionBox_x, int[][] positionBox_y,
                                   int[][] positionZones, Object payload, int n, Map<String, CPIntVar> IDENTIFIERS,
                                   AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, boolean minrange) {


        int[] zonesAndK = (int[]) payload;
        int zone_start = zonesAndK[0];
        int zone_end = zonesAndK[1];
        int k = zonesAndK[2];

        if (zone_start == zone_end) {
            throw new IllegalArgumentException("Cannot move from one zone to the same zone.");
        }

        Set<Integer> leftTeamIds = new HashSet<>();
        Set<Integer> rightTeamIds = new HashSet<>();

        for (int i = 0; i < teams.length; i++) {
            if (teams[i] == 0) {
                leftTeamIds.add(i);
            } else if (teams[i] == 1) {
                rightTeamIds.add(i);
            }
        }


        Team team = (Team) subject;

        if (team.name().equals("left")) {
            team = new Team("left", leftTeamIds);
        } else if (team.name().equals("right")) {
            team = new Team("right", rightTeamIds);
        }

        CPIntVar[] playerIds = new CPIntVar[team.players.size()];
        int teamPlayer = 0;

        if (isNegated) {
            throw new IllegalArgumentException("Negation of TEAM_MOVE_TO is not supported.");
        } else {
            CPIntervalVar[] chosenIntervals = new CPIntervalVar[team.players.size()];
            for (Player player : team.players()) {

                CPIntVar player_id;
                chosenIntervals[teamPlayer] = makeOptionalInterval(cp, 0, n + 1);

                if (IDENTIFIERS.get(player.name()) == null) {
                    player_id = makeIntVar(cp, teams.length);
                    rememberIdentity(IDENTIFIERS, player.name, player_id, eventInterval);
                } else {
                    player_id = IDENTIFIERS.get(player.name);
                    noteIdentityUse(player.name, eventInterval);
                }
                playerIds[teamPlayer] = player_id;
                ExtendedCPVar player_id_ext = new ExtendedCPVar(
                        player_id,
                        counterVars.incrementAndGet(),
                        action.name + "_player_id",
                        counterEvent.get(),
                        false
                );
                extVars.add(player_id_ext);

                if (player.id() != null) {
                    cp.post(eq(player_id, player.id()));
                } else if (player.team() != null) {
                    int playerTeam = player.team().equals("left") ? 0 : 1;
                    for (int i = 0; i < teams.length; i++) {
                        if (teams[i] != playerTeam) {
                            player_id.remove(i);
                        }
                    }
                } else {
                    for (int i = 0; i < teams.length; i++) {
                        if (teams[i] != 0 && teams[i] != 1) {
                            player_id.remove(i);
                        }
                    }
                }

                int[] possible_players = new int[teams.length];
                player_id.fillArray(possible_players);
                CPBoolVar[] isThisPlayerVars = new CPBoolVar[teams.length];

                for (int pl_id : possible_players) {
                    if (pl_id == 0) {
                        continue;
                    }
                    CPIntervalVar thisPlayerInterval = makeIntervalVar(cp, false, 0, n + 1);
                    try {
                        if (minrange) {
                            cp.post(new SubstringRegular(positionZones[pl_id], thisPlayerInterval, Automaton.A_NOTBSTAR_B(teams.length, zone_start, zone_end)));
                        } else {
                            CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n + 1);
                            int[] paddedArray = Automaton.pad(positionZones[pl_id]);
                            cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.PAD_APLUS_PADSTAR_BPLUS_PAD(teams.length, zone_start, zone_end)));
                            cp.post(eq(start(thisPlayerInterval), start(paddedInterval)));
                            cp.post(eq(end(thisPlayerInterval), minus(end(paddedInterval), 2)));
                        }
                        isThisPlayerVars[pl_id] = isEq(sum(isEq(player_id, pl_id), isEq(startOr(chosenIntervals[teamPlayer], 0), start(thisPlayerInterval)), isEq(endOr(chosenIntervals[teamPlayer], n), end(thisPlayerInterval))), 3);

                    } catch (InconsistencyException e) {
                        isThisPlayerVars[pl_id] = makeBoolVar(cp);
                        isThisPlayerVars[pl_id].fix(false);
                    }
                }

                postWhenPresent(cp, eventInterval, isEq(sum(Arrays.stream(isThisPlayerVars)
                        .filter(Objects::nonNull)
                        .toArray(CPBoolVar[]::new)), 1));
                teamPlayer++;
            }

            postWhenPresent(cp, eventInterval, isGe(sum(Arrays.stream(chosenIntervals).map(CPIntervalVar::status).toArray(CPIntVar[]::new)), k));

            CPIntVar[] starts = Arrays.stream(chosenIntervals).map(itv -> startOr(itv, 0)).toArray(CPIntVar[]::new);
            postWhenPresent(cp, eventInterval, isEq(event_start, max(starts)));
            CPIntVar[] ends = Arrays.stream(chosenIntervals).map(itv -> endOr(itv, n)).toArray(CPIntVar[]::new);
            postWhenPresent(cp, eventInterval, isEq(event_end, minimum(ends)));

        }

        // spatial constraints
        if (event_circle || event_rectangle || total_circle || total_rectangle) {

            //Transposed matrices
            int[][] positionBox_x_T = new int[n][teams.length];
            int[][] positionBox_y_T = new int[n][teams.length];
            for (int playerId = 0; playerId < teams.length; playerId++) {
                for (int frame = 0; frame < n; frame++) {
                    positionBox_x_T[frame][playerId] = positionBox_x[playerId][frame];
                }
            }
            for (int playerId = 0; playerId < teams.length; playerId++) {
                for (int frame = 0; frame < n; frame++) {
                    positionBox_y_T[frame][playerId] = positionBox_y[playerId][frame];
                }
            }

            if (event_circle || total_circle) {
                List<int[]> circles = new ArrayList<>();
                if (event_circle)
                    circles.add(new int[]{(int) Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                if (total_circle)
                    circles.add(new int[]{(int) Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                for (int[] circle : circles) {
                    for (int plIdx = 0; plIdx < playerIds.length; plIdx++) {
                        CPIntVar pl = playerIds[plIdx];
                        int[] possible_players = new int[teams.length];
                        pl.fillArray(possible_players);

                        CPBoolVar[] pl_inside_circle = new CPBoolVar[n];
                        for (int f = 0; f < n; f++) {
                            int[] insideCircle = new int[teams.length];
                            for (int pid = 0; pid < teams.length; pid++) {
                                int dx = positionBox_x_T[f][pid] - circle[1];
                                int dy = positionBox_y_T[f][pid] - circle[2];
                                insideCircle[pid] = (dx * dx + dy * dy <= circle[0]) ? 1 : 0;
                            }
                            pl_inside_circle[f] = isEq(element(insideCircle, pl), 1);
                        }
                        cp.post(new TrueInterval(pl_inside_circle, eventInterval));
                    }
                }
            } if (event_rectangle || total_rectangle) {
                List<int[]> rectangles = new ArrayList<>();
                if (event_rectangle)
                    rectangles.add(new int[]{event_xtop, event_ytop, event_w, event_h});
                if (total_rectangle)
                    rectangles.add(new int[]{total_xtop, total_ytop, total_w, total_h});

                for (int[] rect : rectangles) {
                    for (CPIntVar pl : playerIds) {
                        CPBoolVar[] pl_inside_rect = makeBoolVarArray(cp, n);
                        for (int f = 0; f < n; f++) {
                            CPIntVar distx = minus(element(positionBox_x_T[f], pl), rect[0]);
                            CPIntVar disty = minus(element(positionBox_y_T[f], pl), rect[1]);
                            //inside the rectangle -> equivalent to and(isLe(distx, rect[2]), isLe(disty, rect[3]))
                            pl_inside_rect[f] = not(isOr(isGe(distx, rect[2]), isGe(disty, rect[3])));
                        }
                        cp.post(new TrueInterval(pl_inside_rect, eventInterval));
                    }
                }
            }
        }
    }

    public void model_POSITION(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntervalVar eventInterval, CPIntVar event_start, CPIntVar event_end,
                               boolean event_circle, boolean event_rectangle, boolean total_circle,
                               boolean total_rectangle, int event_xcenter, int event_ycenter, int event_radius,
                               int event_xtop, int event_ytop, int event_w, int event_h, int total_xcenter, int total_ycenter,
                               double total_radius, int total_xtop, int total_ytop, int total_w, int total_h, Entity subject, int[] teams, int[][] positionBox_x,
                               int[][] positionBox_y, int[][] positionZones, Object payload, int n,
                               Map<String, CPIntVar> IDENTIFIERS, AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, int ball_idx, int nbZones) {

        if (subject instanceof Player player) {
            int[] zones = (int[]) payload;
            if (zones.length == 0) {
                throw new IllegalArgumentException("At least one zone must be specified for POSITION");
            }

            CPIntVar player_id = makeIntVar(cp, teams.length);

            if (IDENTIFIERS.get(player.name()) == null) {
                rememberIdentity(IDENTIFIERS, player.name, player_id, eventInterval);
            } else {
                player_id = IDENTIFIERS.get(player.name);
                noteIdentityUse(player.name, eventInterval);
            }

            ExtendedCPVar player_id_ext = new ExtendedCPVar(
                    player_id,
                    counterVars.incrementAndGet(),
                    action.name + "_player_id",
                    counterEvent.get(),
                    isNegated
            );
            extVars.add(player_id_ext);

            if (player.id() != null) {
                cp.post(eq(player_id, player.id()));
            } else if (player.team() != null) {
                int team = player.team().equals("left") ? 0 : 1;
                for (int i = 0; i < teams.length; i++) {
                    if (teams[i] != team) {
                        player_id.remove(i);
                    }
                }
            } else {
                for (int i = 0; i < teams.length; i++) {
                    if (teams[i] != 0 && teams[i] != 1) {
                        player_id.remove(i);
                    }
                }
            }

            int[] possible_players = new int[teams.length];
            player_id.fillArray(possible_players);
            CPBoolVar[] isThisPlayerVars = new CPBoolVar[teams.length];

            for (int pl_id : possible_players) {
                if (pl_id == 0) {
                    continue;
                }
                CPIntervalVar thisPlayerTrueInterval = makeIntervalVar(cp, false, 0, n + 1);
                try {
                    CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n + 1);
                    if (isNegated) {
                        int[] paddedArray = Automaton.pad(positionZones[pl_id], zones[0]);
                        int[] otherZones = java.util.stream.IntStream.rangeClosed(0, nbZones)
                                .filter(z -> Arrays.stream(zones).noneMatch(v -> v == z))
                                .toArray();
                        cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones, Arrays.stream(otherZones).boxed().collect(Collectors.toSet()))));
                    } else {
                        int[] paddedArray = Automaton.pad(positionZones[pl_id]);
                        cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones, Arrays.stream(zones).boxed().collect(Collectors.toSet()))));
                    }
                    cp.post(eq(start(thisPlayerTrueInterval), start(paddedInterval)));
                    cp.post(eq(end(thisPlayerTrueInterval), minus(end(paddedInterval), 2)));

                    isThisPlayerVars[pl_id] = isEq(player_id, pl_id);
                    impliesWhenPresent(cp, eventInterval, isThisPlayerVars[pl_id],
                            isEq(start(thisPlayerTrueInterval), startOf(eventInterval)));
                    impliesWhenPresent(cp, eventInterval, isThisPlayerVars[pl_id],
                            isEq(end(thisPlayerTrueInterval), endOf(eventInterval)));

                } catch (InconsistencyException e) {
                    isThisPlayerVars[pl_id] = makeBoolVar(cp);
                    isThisPlayerVars[pl_id].fix(false);
                }
            }
            postWhenPresent(cp, eventInterval, isEq(sum(Arrays.stream(isThisPlayerVars)
                    .filter(Objects::nonNull)
                    .toArray(CPBoolVar[]::new)), 1));


            // spatial constraints
            if (event_circle || event_rectangle || total_circle || total_rectangle) {

                //Transposed matrices
                int[][] positionBox_x_T = new int[n][teams.length];
                int[][] positionBox_y_T = new int[n][teams.length];
                for (int playerId = 0; playerId < teams.length; playerId++) {
                    for (int frame = 0; frame < n; frame++) {
                        positionBox_x_T[frame][playerId] = positionBox_x[playerId][frame];
                    }
                }
                for (int playerId = 0; playerId < teams.length; playerId++) {
                    for (int frame = 0; frame < n; frame++) {
                        positionBox_y_T[frame][playerId] = positionBox_y[playerId][frame];
                    }
                }

                if (event_circle || total_circle) {
                    List<int[]> circles = new ArrayList<>();
                    if (event_circle)
                        circles.add(new int[]{(int) Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                    if (total_circle)
                        circles.add(new int[]{(int) Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                    for (int[] circle : circles) {
                        CPBoolVar[] pl_inside_circle = new CPBoolVar[n]; //true = 1, false = 0
                        for (int f = 0; f < n; f++) {
                            int[] insideCircle = new int[teams.length];
                            for (int pid = 0; pid < teams.length; pid++) {
                                int dx = positionBox_x_T[f][pid] - circle[1];
                                int dy = positionBox_y_T[f][pid] - circle[2];
                                insideCircle[pid] = (dx * dx + dy * dy <= circle[0]) ? 1 : 0;
                            }
                            pl_inside_circle[f] = isEq(element(insideCircle, player_id), 1);
                        }
                        cp.post(new TrueInterval(pl_inside_circle, eventInterval));
                    }
                } if (event_rectangle || total_rectangle) {
                    List<int[]> rectangles = new ArrayList<>();
                    if (event_rectangle)
                        rectangles.add(new int[]{event_xtop, event_ytop, Math.abs(event_w), Math.abs(event_h)});
                    if (total_rectangle)
                        rectangles.add(new int[]{total_xtop, total_ytop, Math.abs(total_w), Math.abs(total_h)});

                    for (int[] rect : rectangles) {
                        CPBoolVar[] pl_inside_rect = makeBoolVarArray(cp, n);
                        for (int f = 0; f < n; f++) {
                            CPIntVar bx = element(positionBox_x_T[f], player_id);
                            CPIntVar by = element(positionBox_y_T[f], player_id);
                            CPBoolVar upper_bound_x = isLe(bx, rect[0] + rect[2]);
                            CPBoolVar lower_bound_x = isGe(bx, rect[0]);
                            CPBoolVar upper_bound_y = isLe(by, rect[1]);
                            CPBoolVar lower_bound_y = isGe(by, rect[1] - rect[3]);
                            pl_inside_rect[f] = isEq(sum(upper_bound_x, lower_bound_x, upper_bound_y, lower_bound_y), 4); //inside the rectangle
                        }
                        cp.post(new TrueInterval(pl_inside_rect, eventInterval));
                    }
                }
            }

        } else if (subject instanceof Team team) {

            int[] zonesAndK = (int[]) payload;
            int[] zones = Arrays.copyOf(zonesAndK, zonesAndK.length - 1);
            if (zones.length == 0) {
                throw new IllegalArgumentException("At least one zone must be specified for POSITION");
            }
            int k = zonesAndK[zonesAndK.length - 1];

            Set<Integer> leftTeamIds = new HashSet<>();
            Set<Integer> rightTeamIds = new HashSet<>();

            for (int i = 0; i < teams.length; i++) {
                if (teams[i] == 0) {
                    leftTeamIds.add(i);
                } else if (teams[i] == 1) {
                    rightTeamIds.add(i);
                }
            }

            if (team.name().equals("left")) {
                team = new Team("left", leftTeamIds);
            } else if (team.name().equals("right")) {
                team = new Team("right", rightTeamIds);
            }

            CPIntVar[] playerIds = new CPIntVar[team.players.size()];
            int teamPlayer = 0;

            CPIntervalVar[] chosenIntervals = new CPIntervalVar[team.players.size()];
            for (Player player : team.players()) {

                CPIntVar player_id;
                chosenIntervals[teamPlayer] = makeOptionalInterval(cp, 0, n + 1);

                if (IDENTIFIERS.get(player.name()) == null) {
                    player_id = makeIntVar(cp, teams.length);
                    rememberIdentity(IDENTIFIERS, player.name, player_id, eventInterval);
                } else {
                    player_id = IDENTIFIERS.get(player.name);
                    noteIdentityUse(player.name, eventInterval);
                }
                playerIds[teamPlayer] = player_id;
                ExtendedCPVar player_id_ext = new ExtendedCPVar(
                        player_id,
                        counterVars.incrementAndGet(),
                        action.name + "_player_id",
                        counterEvent.get(),
                        false
                );
                extVars.add(player_id_ext);

                if (player.id() != null) {
                    cp.post(eq(player_id, player.id()));
                } else if (player.team() != null) {
                    int playerTeam = player.team().equals("left") ? 0 : 1;
                    for (int i = 0; i < teams.length; i++) {
                        if (teams[i] != playerTeam) {
                            player_id.remove(i);
                        }
                    }
                } else {
                    for (int i = 0; i < teams.length; i++) {
                        if (teams[i] != 0 && teams[i] != 1) {
                            player_id.remove(i);
                        }
                    }
                }

                int[] possible_players = new int[teams.length];
                player_id.fillArray(possible_players);
                CPBoolVar[] isThisPlayerVars = new CPBoolVar[teams.length];

                for (int pl_id : possible_players) {
                    if (pl_id == 0) {
                        continue;
                    }
                    CPIntervalVar thisPlayerInterval = makeIntervalVar(cp, false, 0, n + 1);
                    try {
                        CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n + 1);

                        if (isNegated) {
                            int[] paddedArray = Automaton.pad(positionZones[pl_id], zones[0]);
                            int[] otherZones = java.util.stream.IntStream.rangeClosed(0, nbZones)
                                    .filter(z -> Arrays.stream(zones).noneMatch(v -> v == z))
                                    .toArray();
                            cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones, Arrays.stream(otherZones).boxed().collect(Collectors.toSet()))));

                        } else {
                            int[] paddedArray = Automaton.pad(positionZones[pl_id]);
                            cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones, Arrays.stream(zones).boxed().collect(Collectors.toSet()))));
                        }
                        cp.post(eq(start(thisPlayerInterval), start(paddedInterval)));
                        cp.post(eq(end(thisPlayerInterval), minus(end(paddedInterval), 2)));

                        isThisPlayerVars[pl_id] = isEq(sum(isEq(player_id, pl_id), isEq(startOr(chosenIntervals[teamPlayer], 0), start(thisPlayerInterval)), isEq(endOr(chosenIntervals[teamPlayer], n), end(thisPlayerInterval))), 3);

                    } catch (InconsistencyException e) {
                        isThisPlayerVars[pl_id] = makeBoolVar(cp);
                        isThisPlayerVars[pl_id].fix(false);
                    }
                }

                postWhenPresent(cp, eventInterval, isEq(sum(Arrays.stream(isThisPlayerVars)
                        .filter(Objects::nonNull)
                        .toArray(CPBoolVar[]::new)), 1));
                teamPlayer++;
            }

            postWhenPresent(cp, eventInterval, isGe(sum(Arrays.stream(chosenIntervals).map(CPIntervalVar::status).toArray(CPIntVar[]::new)), k));

            CPIntVar[] starts = Arrays.stream(chosenIntervals).map(itv -> startOr(itv, 0)).toArray(CPIntVar[]::new);
            postWhenPresent(cp, eventInterval, isEq(event_start, max(starts)));
            CPIntVar[] ends = Arrays.stream(chosenIntervals).map(itv -> endOr(itv, n)).toArray(CPIntVar[]::new);
            postWhenPresent(cp, eventInterval, isEq(event_end, minimum(ends)));


            // spatial constraints
            if (event_circle || event_rectangle || total_circle || total_rectangle) {

                //Transposed matrices
                int[][] positionBox_x_T = new int[n][teams.length];
                int[][] positionBox_y_T = new int[n][teams.length];
                for (int playerId = 0; playerId < teams.length; playerId++) {
                    for (int frame = 0; frame < n; frame++) {
                        positionBox_x_T[frame][playerId] = positionBox_x[playerId][frame];
                    }
                }
                for (int playerId = 0; playerId < teams.length; playerId++) {
                    for (int frame = 0; frame < n; frame++) {
                        positionBox_y_T[frame][playerId] = positionBox_y[playerId][frame];
                    }
                }

                if (event_circle || total_circle) {
                    List<int[]> circles = new ArrayList<>();
                    if (event_circle)
                        circles.add(new int[]{(int) Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                    if (total_circle)
                        circles.add(new int[]{(int) Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                    for (int[] circle : circles) {
                        for (int plIdx = 0; plIdx < playerIds.length; plIdx++) {
                            CPIntVar pl = playerIds[plIdx];
                            int[] possible_players = new int[teams.length];
                            pl.fillArray(possible_players);

                            CPBoolVar[] pl_inside_circle = new CPBoolVar[n];
                            for (int f = 0; f < n; f++) {
                                int[] insideCircle = new int[teams.length];
                                for (int pid = 0; pid < teams.length; pid++) {
                                    int dx = positionBox_x_T[f][pid] - circle[1];
                                    int dy = positionBox_y_T[f][pid] - circle[2];
                                    insideCircle[pid] = (dx * dx + dy * dy <= circle[0]) ? 1 : 0;
                                }
                                pl_inside_circle[f] = isEq(element(insideCircle, pl), 1);
                            }
                            cp.post(new TrueInterval(pl_inside_circle, eventInterval));
                        }
                    }
                } if (event_rectangle || total_rectangle) {
                    List<int[]> rectangles = new ArrayList<>();
                    if (event_rectangle)
                        rectangles.add(new int[]{event_xtop, event_ytop, event_w, event_h});
                    if (total_rectangle)
                        rectangles.add(new int[]{total_xtop, total_ytop, total_w, total_h});

                    for (int[] rect : rectangles) {
                        for (CPIntVar pl : playerIds) {
                            CPBoolVar[] pl_inside_rect = makeBoolVarArray(cp, n);
                            for (int f = 0; f < n; f++) {
                                CPIntVar distx = minus(element(positionBox_x_T[f], pl), rect[0]);
                                CPIntVar disty = minus(element(positionBox_y_T[f], pl), rect[1]);
                                //inside the rectangle -> equivalent to and(isLe(distx, rect[2]), isLe(disty, rect[3]))
                                pl_inside_rect[f] = not(isOr(isGe(distx, rect[2]), isGe(disty, rect[3])));
                            }
                            cp.post(new TrueInterval(pl_inside_rect, eventInterval));
                        }
                    }
                }
            }

        } else { //ball
            int[] zones = (int[]) payload;
            if (zones.length == 0) {
                throw new IllegalArgumentException("At least one zone must be specified for POSITION of the ball");
            }
            CPIntervalVar paddedInterval = paddedFor(cp, eventInterval, n + 2);
            if (isNegated) {
                int[] paddedArray = Automaton.pad(positionZones[ball_idx], zones[0]);
                int[] otherZones = java.util.stream.IntStream.rangeClosed(0, nbZones)
                        .filter(z -> Arrays.stream(zones).noneMatch(v -> v == z))
                        .toArray();
                cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones, Arrays.stream(otherZones).boxed().collect(Collectors.toSet()))));
            } else {
                int[] paddedArray = Automaton.pad(positionZones[ball_idx]);
                cp.post(new SubstringRegular(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones, Arrays.stream(zones).boxed().collect(Collectors.toSet()))));
            }
            linkPadded(cp, eventInterval, paddedInterval);

            if (event_circle || event_rectangle || total_circle || total_rectangle) {
                if (event_circle || total_circle) {
                    List<double[]> circles = new ArrayList<>();
                    if (event_circle)
                        circles.add(new double[]{Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                    if (total_circle)
                        circles.add(new double[]{Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                    for (double[] circle : circles) {
                        int[] ball_inside_circle = new int[n]; //true = 1, false = 0
                        for (int f = 0; f < n; f++) {
                            double dist_x = Math.pow(positionBox_x[ball_idx][f] - circle[1], 2);
                            double dist_y = Math.pow(positionBox_y[ball_idx][f] - circle[2], 2);

                            ball_inside_circle[f] = dist_x + dist_y <= circle[0] ? 1 : 0;
                        }
                        cp.post(new SubstringRegular(ball_inside_circle, eventInterval, Automaton.APLUS(1, Set.of(1))));
                    }
                } if (event_rectangle || total_rectangle) {
                    List<double[]> rectangles = new ArrayList<>();
                    if (event_rectangle)
                        rectangles.add(new double[]{event_xtop, event_ytop, event_w, event_h});
                    if (total_rectangle)
                        rectangles.add(new double[]{total_xtop, total_ytop, total_w, total_h});

                    for (double[] rect : rectangles) {
                        int[] ball_inside_rectangle = new int[n];

                        for (int f = 0; f < n; f++) {
                            double bx = positionBox_x[ball_idx][f];
                            double by = positionBox_y[ball_idx][f];
                            ball_inside_rectangle[f] = (bx >= rect[0] && bx <= rect[0] + rect[2] && by <= rect[1] && by >= rect[1] - rect[3]) ? 1 : 0;
                        }
                        cp.post(new SubstringRegular(ball_inside_rectangle, eventInterval, Automaton.APLUS(1, Set.of(1))));
                    }
                }
            }
        }
    }

    public void model_POSSESSION(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntervalVar eventInterval, CPIntVar event_start, CPIntVar event_end,
                                 int ball_idx, int n, int[] possession, boolean event_circle,
                                 boolean event_rectangle, boolean total_circle, boolean total_rectangle, int event_xcenter,
                                 int event_ycenter, int event_radius, int event_xtop, int event_ytop,
                                 int event_w, int event_h, int total_xcenter, int total_ycenter, int total_radius,
                                 int total_xtop, int total_ytop, int total_w, int total_h,
                                 Entity subject, Map<String, CPIntVar> IDENTIFIERS, AtomicInteger counterVars, Action action,
                                 ArrayList<ExtendedCPVar> extVars, int[] teams, int[][] positionBox_x, int[][] positionBox_y) {

        Set<Integer> leftTeamIds = new HashSet<>();
        Set<Integer> rightTeamIds = new HashSet<>();

        for (int i = 0; i < teams.length; i++) {
            if (teams[i] == 0) {
                leftTeamIds.add(i);
            } else if (teams[i] == 1) {
                rightTeamIds.add(i);
            }
        }

        if (subject instanceof Player player) {

            CPIntVar player_id = makeIntVar(cp, teams.length);

            CPIntVar possessionAtStart = element(possession, startOf(eventInterval));

            if (IDENTIFIERS.get(player.name()) == null) {
                if (!isNegated) player_id = boundWhenPresent(cp, eventInterval, possessionAtStart, teams.length);
                rememberIdentity(IDENTIFIERS, player.name, player_id, eventInterval);
            } else {
                player_id = IDENTIFIERS.get(player.name);
                noteIdentityUse(player.name, eventInterval);
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
                    }
                }
            }

            CPIntervalVar paddedInterval = paddedFor(cp, eventInterval, n + 2);
            if (isNegated) {
                int[] paddedPossession = Automaton.pad(possession, validIds.iterator().next());
                Set<Integer> allPossessionValues = Arrays.stream(possession).boxed().collect(Collectors.toSet());
                Set<Integer> complementIds = allPossessionValues.stream()
                        .filter(v -> !validIds.contains(v))
                        .collect(Collectors.toSet());
                cp.post(new SubstringRegular(paddedPossession, paddedInterval, Automaton.NOTA_APLUS_NOTA(teams.length, complementIds)));
            } else {
                int[] paddedPossession = Automaton.pad(possession);
                cp.post(new SubstringRegular(paddedPossession, paddedInterval, Automaton.NOTA_APLUS_NOTA(teams.length, validIds)));

            }
            linkPadded(cp, eventInterval, paddedInterval);

            // spatial constraints
            if (event_circle || event_rectangle || total_circle || total_rectangle) {
                if (event_circle || total_circle) {
                    List<double[]> circles = new ArrayList<>();
                    if (event_circle)
                        circles.add(new double[]{Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                    if (total_circle)
                        circles.add(new double[]{Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                    for (double[] circle : circles) {
                        int[] ball_inside_circle = new int[n];
                        for (int f = 0; f < n; f++) {
                            double dist_x = Math.pow(positionBox_x[ball_idx][f] - circle[1], 2);
                            double dist_y = Math.pow(positionBox_y[ball_idx][f] - circle[2], 2);
                            ball_inside_circle[f] = dist_x + dist_y <= circle[0] ? 1 : 0;
                        }
                        cp.post(new SubstringRegular(ball_inside_circle, eventInterval, Automaton.APLUS(teams.length, Set.of(1))));
                    }
                } if (event_rectangle || total_rectangle) {
                    List<double[]> rectangles = new ArrayList<>();
                    if (event_rectangle)
                        rectangles.add(new double[]{event_xtop, event_ytop, event_w, event_h});
                    if (total_rectangle)
                        rectangles.add(new double[]{total_xtop, total_ytop, total_w, total_h});

                    for (double[] rect : rectangles) {
                        int[] ball_inside_rectangle = new int[n];
                        for (int f = 0; f < n; f++) {
                            double bx = positionBox_x[ball_idx][f];
                            double by = positionBox_y[ball_idx][f];
                            ball_inside_rectangle[f] = (bx >= rect[0] && bx <= rect[0] + rect[2] && by <= rect[1] && by >= rect[1] - rect[3]) ? 1 : 0;
                        }
                        cp.post(new SubstringRegular(ball_inside_rectangle, eventInterval, Automaton.APLUS(teams.length, Set.of(1))));
                    }
                }
            }


        } else if (subject instanceof Team team) {

            CPIntVar[] playerIds;

            if (team.name().equals("left")) {
                team = new Team("left", leftTeamIds);
                playerIds = new CPIntVar[team.players.size()];
                Iterator<Integer> ids = leftTeamIds.iterator();
                for  (int i = 0; i < team.players.size() && ids.hasNext(); i++) {
                    CPIntVar plI = makeIntVar(cp, teams.length);
                    int id = ids.next();
                    plI.fix(id);
                    playerIds[i] = plI;
                }
            } else if (team.name().equals("right")) {
                team = new Team("right", rightTeamIds);
                playerIds = new CPIntVar[team.players.size()];
                Iterator<Integer> ids = rightTeamIds.iterator();
                for  (int i = 0; i < team.players.size() && ids.hasNext(); i++) {
                    CPIntVar plI = makeIntVar(cp, teams.length);
                    int id = ids.next();
                    plI.fix(id);
                    playerIds[i] = plI;
                }
            } else {
                playerIds = new CPIntVar[team.players.size()];
            }

            int teamPlayer = 0;

            //if all players are defined by their IDs
            if (team.players.stream().allMatch(p -> p.id() != null)) {
                //set of all IDs of the team
                Set<Integer> teamIds = team.players.stream().map(Player::id).collect(Collectors.toSet());

                if (isNegated) {
                    CPIntervalVar paddedInterval = paddedFor(cp, eventInterval, n + 2);
                    int[] paddedPossession = Automaton.pad(possession, teamIds.iterator().next());
                    Set<Integer> allPossessionValues = Arrays.stream(possession).boxed().collect(Collectors.toSet());
                    Set<Integer> complementIds = allPossessionValues.stream()
                            .filter(v -> !teamIds.contains(v))
                            .collect(Collectors.toSet());
                    cp.post(new SubstringRegular(paddedPossession, paddedInterval, Automaton.NOTA_APLUS_NOTA(teams.length, complementIds)));
                    linkPadded(cp, eventInterval, paddedInterval);
                } else {
                    CPIntervalVar paddedInterval = paddedFor(cp, eventInterval, n + 2);
                    int[] paddedPossession = Automaton.pad(possession);
                    cp.post(new SubstringRegular(paddedPossession, paddedInterval, Automaton.NOTA_APLUS_NOTA(teams.length, teamIds)));
                    linkPadded(cp, eventInterval, paddedInterval);

                }

            } else {
                CPIntervalVar[] chosenIntervals = new CPIntervalVar[team.players.size()];
                for (Player player : team.players()) {

                    CPIntVar player_id;
                    chosenIntervals[teamPlayer] = makeOptionalInterval(cp, 0, n + 1);

                    if (IDENTIFIERS.get(player.name()) == null) {
                        player_id = makeIntVar(cp, teams.length);
                        rememberIdentity(IDENTIFIERS, player.name, player_id, eventInterval);
                    } else {
                        player_id = IDENTIFIERS.get(player.name);
                        noteIdentityUse(player.name, eventInterval);
                    }
                    playerIds[teamPlayer] = player_id;
                    ExtendedCPVar player_id_ext = new ExtendedCPVar(
                            player_id,
                            counterVars.incrementAndGet(),
                            action.name + "_player_id",
                            counterEvent.get(),
                            false
                    );
                    extVars.add(player_id_ext);

                    if (player.id() != null) {
                        cp.post(eq(player_id, player.id()));
                    } else if (player.team() != null) {
                        int playerTeam = player.team().equals("left") ? 0 : 1;
                        for (int i = 0; i < teams.length; i++) {
                            if (teams[i] != playerTeam) {
                                player_id.remove(i);
                            }
                        }
                    } else {
                        for (int i = 0; i < teams.length; i++) {
                            if (teams[i] != 0 && teams[i] != 1) {
                                player_id.remove(i);
                            }
                        }
                    }

                    int[] possible_players = new int[teams.length];
                    player_id.fillArray(possible_players);
                    CPBoolVar[] isThisPlayerVars = new CPBoolVar[teams.length];

                    for (int pl_id : possible_players) {
                        if (pl_id == 0) {
                            continue;
                        }
                        CPIntervalVar thisPlayerInterval = makeIntervalVar(cp, false, 0, n + 1);
                        try {
                            CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n + 1);
                            if (isNegated) {
                                int[] paddedPossession = Automaton.pad(possession, pl_id);
                                Set<Integer> allPossessionValues = Arrays.stream(possession).boxed().collect(Collectors.toSet());
                                Set<Integer> complementIds = allPossessionValues.stream()
                                        .filter(v -> v != pl_id)
                                        .collect(Collectors.toSet());
                                cp.post(new SubstringRegular(paddedPossession, paddedInterval, Automaton.NOTA_APLUS_NOTA(teams.length, complementIds)));
                            } else {
                                int[] paddedPossession = Automaton.pad(possession);
                                cp.post(new SubstringRegular(paddedPossession, paddedInterval, Automaton.NOTA_APLUS_NOTA(teams.length, Set.of(pl_id))));
                            }
                            cp.post(eq(start(thisPlayerInterval), start(paddedInterval)));
                            cp.post(eq(end(thisPlayerInterval), minus(end(paddedInterval), 2)));

                            isThisPlayerVars[pl_id] = isEq(sum(isEq(player_id, pl_id), isEq(startOr(chosenIntervals[teamPlayer], 0), start(thisPlayerInterval)), isEq(endOr(chosenIntervals[teamPlayer], n), end(thisPlayerInterval))), 3);

                        } catch (InconsistencyException e) {
                            isThisPlayerVars[pl_id] = makeBoolVar(cp);
                            isThisPlayerVars[pl_id].fix(false);
                        }
                    }

                    try {
                        cp.post(eq(sum(Arrays.stream(isThisPlayerVars)
                                .filter(Objects::nonNull)
                                .toArray(CPBoolVar[]::new)), 1));
                        chosenIntervals[teamPlayer].setPresent();
                    } catch (InconsistencyException e) {
                        chosenIntervals[teamPlayer].setAbsent();
                    }
                    teamPlayer++;
                }

                CPBoolVar[] isChosen = new CPBoolVar[chosenIntervals.length];
                for (int i = 0; i < chosenIntervals.length; i++) {
                    isChosen[i] = isEq(
                            sum(
                                    chosenIntervals[i].status(),
                                    isEq(event_start, startOr(chosenIntervals[i], -1)),
                                    isEq(event_end, endOr(chosenIntervals[i], -1))
                            ), 3);
                }
                postWhenPresent(cp, eventInterval, isEq(sum(isChosen), 1));
            }


            // spatial constraints
            if (event_circle || event_rectangle || total_circle || total_rectangle) {

                //Transposed matrices
                int[][] positionBox_x_T = new int[n][teams.length];
                int[][] positionBox_y_T = new int[n][teams.length];
                for (int playerId = 0; playerId < teams.length; playerId++) {
                    for (int frame = 0; frame < n; frame++) {
                        positionBox_x_T[frame][playerId] = positionBox_x[playerId][frame];
                    }
                }
                for (int playerId = 0; playerId < teams.length; playerId++) {
                    for (int frame = 0; frame < n; frame++) {
                        positionBox_y_T[frame][playerId] = positionBox_y[playerId][frame];
                    }
                }

                if (event_circle || total_circle) {
                    List<int[]> circles = new ArrayList<>();
                    if (event_circle)
                        circles.add(new int[]{(int) Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                    if (total_circle)
                        circles.add(new int[]{(int) Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                    for (int[] circle : circles) {
                        for (int plIdx = 0; plIdx < playerIds.length; plIdx++) {
                            CPIntVar pl = playerIds[plIdx];
                            int[] possible_players = new int[teams.length];
                            pl.fillArray(possible_players);

                            CPBoolVar[] pl_inside_circle = new CPBoolVar[n];
                            for (int f = 0; f < n; f++) {
                                int[] insideCircle = new int[teams.length];
                                for (int pid = 0; pid < teams.length; pid++) {
                                    int dx = positionBox_x_T[f][pid] - circle[1];
                                    int dy = positionBox_y_T[f][pid] - circle[2];
                                    insideCircle[pid] = (dx * dx + dy * dy <= circle[0]) ? 1 : 0;
                                }
                                pl_inside_circle[f] = isEq(element(insideCircle, pl), 1);
                            }
                            cp.post(new TrueInterval(pl_inside_circle, eventInterval));
                        }
                    }
                } if (event_rectangle || total_rectangle) {
                    List<int[]> rectangles = new ArrayList<>();
                    if (event_rectangle)
                        rectangles.add(new int[]{event_xtop, event_ytop, event_w, event_h});
                    if (total_rectangle)
                        rectangles.add(new int[]{total_xtop, total_ytop, total_w, total_h});

                    for (int[] rect : rectangles) {
                        for (CPIntVar pl : playerIds) {
                            CPBoolVar[] pl_inside_rect = makeBoolVarArray(cp, n);
                            for (int f = 0; f < n; f++) {
                                CPIntVar distx = minus(element(positionBox_x_T[f], pl), rect[0]);
                                CPIntVar disty = minus(element(positionBox_y_T[f], pl), rect[1]);
                                //inside the rectangle -> equivalent to and(isLe(distx, rect[2]), isLe(disty, rect[3]))
                                pl_inside_rect[f] = not(isOr(isGe(distx, rect[2]), isGe(disty, rect[3])));
                            }
                            cp.post(new TrueInterval(pl_inside_rect, eventInterval));
                        }
                    }
                }
            }
        }
    }

    /** Creates an interval that may end up absent, and remembers it as such. */
    private CPIntervalVar makeOptionalInterval(CPSolver cp, int lengthMin, int lengthMax) {
        CPIntervalVar itv = makeIntervalVar(cp, lengthMin, lengthMax);
        mayBeAbsent.add(itv);
        return itv;
    }

    /** The event interval each player identity was introduced by. */
    private final Map<String, CPIntervalVar> identityOwner = new HashMap<>();

    /** Names of identities used by at least one event that always happens. */
    private final Set<String> alwaysPresentIdentities = new HashSet<>();

    /** Records {@code name -> var} as an identity introduced by {@code owner}. */
    private void rememberIdentity(Map<String, CPIntVar> identifiers, String name, CPIntVar var, CPIntervalVar owner) {
        identifiers.put(name, var);
        identityOwner.putIfAbsent(name, owner);
        noteIdentityUse(name, owner);
    }

    /** Records that {@code owner} refers to the identity {@code name}. */
    private void noteIdentityUse(String name, CPIntervalVar owner) {
        if (!mayBeAbsent.contains(owner)) alwaysPresentIdentities.add(name);
    }

    /** Posts {@code a != b}, but only while both owning events happen. */
    private void postDistinctWhenBothPresent(CPSolver cp, CPIntervalVar ownerA, CPIntervalVar ownerB,
                                             CPIntVar a, CPIntVar b) {
        List<CPBoolVar> guards = new ArrayList<>(2);
        if (ownerA != null && mayBeAbsent.contains(ownerA)) guards.add(ownerA.status());
        if (ownerB != null && mayBeAbsent.contains(ownerB)) guards.add(ownerB.status());
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
            ExtendedCPVar ev = extVars.get(i.get());
            if ("AND_interval".equals(ev.type) || "OR_interval".equals(ev.type)) {
                CPIntervalVar interval = (CPIntervalVar) ev.var;
                Result.GroupEvent ge = new Result.GroupEvent(ev.event_idx, ev.type.equals("AND_interval") ? "AND" : "OR", ev.subSz, new Result.ResultInterval(interval));
                i.incrementAndGet();
                for (int k = 0; k < ge.subSize && i.get() < extVars.size(); k++) {
                    // every child is parsed so the cursor stays aligned, but an OR branch that was
                    // not taken is absent: it belongs to the model, not to the match
                    Result.ResultEvent child = parseSingleEvent(extVars, i);
                    if (child != null && child.interval.present) ge.children.add(child);
                }
                events.add(ge);
            } else {
                Result.ResultEvent e = parseSingleEvent(extVars, i);
                if (e != null) events.add(e);
            }
        }
        return new Result(solutionIndex, events, instance);
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


