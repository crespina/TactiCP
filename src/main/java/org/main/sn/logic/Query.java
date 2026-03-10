package org.main.sn.logic;

import org.main.sn.dsl.*;
import org.main.util.Automaton;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPVar;
import org.maxicp.cp.engine.core.CPIntervalVar;
import org.maxicp.cp.engine.core.CPSolver;


import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.maxicp.cp.CPFactory.*;

public class Query {

    public List<Result> apply(SelectExpr seq) {

        CPSolver cp = makeSolver();
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

            Map<String, CPIntVar> IDENTIFIERS = new HashMap<>(); //Entity's name -> CP variable

            ArrayList<ExtendedCPVar> extVars = new ArrayList<>();
            ArrayList<CPIntervalVar> intervals = new ArrayList<>();
            AtomicInteger counterVars = new AtomicInteger(-1);
            AtomicInteger counterEvent = new AtomicInteger(0);

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

                for (Event event : steps){
                    if (event instanceof AndEvent andEvent) {
                        intervals.add(makeIntervalVar(cp, false, 0, n+1)); //the whole AndEvent
                        for (Event child : andEvent.children()) {
                            intervals.add(makeIntervalVar(cp, false, 0, n+1));
                        }
                    } else if (event instanceof OrEvent orEvent) {
                        intervals.add(makeIntervalVar(cp, false, 0, n+1)); //the whole OrEvent
                        for (Event child : orEvent.children()) {
                            intervals.add(makeIntervalVar(cp, false, 0, n+1));
                        }
                    } else {
                        intervals.add(makeIntervalVar(cp, false, 0, n+1));
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

                for (Event event : steps) {
                    CPIntervalVar event_interval = intervals.get(counterEvent.get());
                    CPIntVar event_start = start(event_interval);
                    CPIntVar event_end = end(event_interval);
                    cp.post(gt(minus(event_end,1), event_start)); //because end non inclusive

                    modelEvent(event, cp, counterEvent, event_interval, event_start, event_end, ball_idx, n, positionZones, positionBox_x,
                            positionBox_y, total_circle, extVars, intervals, total_rectangle, counterVars, teams,
                            IDENTIFIERS, total_xcenter, possession, total_ycenter, total_radius, total_xtop, total_ytop,
                            total_w, total_h, nbZones);
                }

                //AllDifferent on playersID
                if (!IDENTIFIERS.isEmpty()) cp.post(allDifferent(IDENTIFIERS.values().toArray(CPIntVar[]::new)));

                //total duration
                CPIntervalVar lastVar = intervals.getLast();
                CPIntervalVar firstVar = intervals.getFirst();

                if (total_max_duration != -1) {
                    cp.post(le(sum(end(lastVar), minus(start(firstVar))), total_max_duration));
                }
                if (total_min_duration != -1) {
                    cp.post(ge(sum(end(lastVar), minus(start(firstVar))), total_min_duration));
                }
                if (total_start_min != -1) {
                    cp.post(ge(start(firstVar), total_start_min));
                }
                if (total_start_max != -1) {
                    cp.post(le(start(firstVar), total_start_max));
                }
                if (total_end_min != -1) {
                    cp.post(ge(end(lastVar), total_end_min));
                }
                if (total_end_max != -1) {
                    cp.post(le(end(lastVar), total_end_max));
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
                            intervals.stream().flatMap(itv -> Stream.of(start(itv), end(itv)))
                    ).toArray(CPIntVar[]::new)));
            AtomicInteger solutionCounter = new AtomicInteger(0);

            search.onSolution(() -> {
                int solnb = solutionCounter.getAndIncrement();
                r.add(parseSolution(solnb, extVars, instance.name));
            });

            if (searchMode != -1 || atMost != -1) {
                if (searchMode == 0) search.solve();
                else if (searchMode >= 1) search.solve(statistics -> statistics.numberOfSolutions() == searchMode);
                else if (atMost >= 0) search.solve(statistics -> statistics.numberOfSolutions() <= atMost);
            } else {
                search.solve();
            }

            if (atLeast>=0) {
                if (solutionCounter.get() < atLeast) {
                    //System.out.println("Number of solutions found (" + solutionCounter.get() + ") is less than the specified atLeast value (" + atLeast + ").");
                    r.subList(r.size() -  solutionCounter.get(), r.size()).clear();
                }
            }
        }


        return r;
    }


    // ##################################
    // HELPER METHODS FOR MODELING EVENTS
    // ##################################


    private void modelEvent(Event event, CPSolver cp, AtomicInteger counterEvent, CPIntervalVar event_interval, CPIntVar event_start, CPIntVar event_end,
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
                childrenStarts[childIdx] = start(childInterval);
                childrenStarts[childIdx].removeAbove(n+1);
                childrenEnds[childIdx] = end(childInterval);
                childrenEnds[childIdx].removeAbove(n+1);

                executeStep(children, cp, counterEvent, childInterval, childrenStarts[childIdx], childrenEnds[childIdx], ball_idx, n,
                        positionZones, positionBox_x, positionBox_y, total_circle, extVars, intervals, total_rectangle,
                        counterVars, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                        total_radius, total_xtop, total_ytop, total_w, total_h, true, childIdx, andEvent.children().size(), nbZones);

                childIdx++;
            }

            //define the interval of the AND event
            cp.post(eq(event_start,  max(childrenStarts)));
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
                childrenStarts[childIdx] = start(childInterval);
                childrenStarts[childIdx].removeAbove(n+1);
                childrenEnds[childIdx] = end(childInterval);
                childrenEnds[childIdx].removeAbove(n+1);
                childIdx++;

                executeStep(children, cp, counterEvent, childInterval, childrenStarts[childIdx], childrenEnds[childIdx], ball_idx, n,
                        positionZones, positionBox_x, positionBox_y, total_circle, extVars, intervals, total_rectangle,
                        counterVars, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                        total_radius, total_xtop, total_ytop, total_w, total_h, true, childIdx, orEvent.children().size(), nbZones);
            }

            cp.post(ge(sum(statuses), 1));

            //define the interval of the OR event
            if (orEvent.isNegated) {
                cp.post(eq(event_start, max(childrenStarts)));
                cp.post(eq(event_end, minimum(childrenEnds)));
            } else {
                cp.post(eq(event_start, minimum(childrenStarts)));
                cp.post(eq(event_end, max(childrenEnds)));
            }
        } else {
            executeStep(event, cp, counterEvent, event_interval, event_start, event_end, ball_idx, n,
                    positionZones, positionBox_x, positionBox_y, total_circle, extVars, intervals, total_rectangle,
                    counterVars, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                    total_radius, total_xtop, total_ytop, total_w, total_h, false, 0, 0, nbZones);
        }
    }


    private void executeStep(Event event, CPSolver cp, AtomicInteger counterEvent, CPIntervalVar eventInterval, CPIntVar event_start, CPIntVar event_end,
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

        if (counterEvent.get() != 0 && !isAndEvent) {
            cp.post(endBeforeStart(intervals.get(counterEvent.get() - 1), eventInterval, 1));
        }

        if (event_timeStartMin != -1) {
            cp.post(ge(event_start, event_timeStartMin));
        }
        if (event_timeStartMax != -1) {
            cp.post(le(event_end, event_timeStartMax));
        }
        if (event_timeEndMin != -1) {
            cp.post(ge(event_start, event_timeEndMin));
        }
        if (event_timeEndMax != -1) {
            cp.post(le(event_end, event_timeEndMax));
        }
        if (event_durationMax != -1) {
            cp.post(le(length(eventInterval), event_durationMax));
        }
        if (event_durationMin != -1) {
            cp.post(ge(length(eventInterval), event_durationMin));
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

            case "PLAYER_MOVE_TO" ->
                    model_PLAYER_MOVE_TO(cp, counterEvent, isNegated, eventInterval, event_circle,
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

        if (isNegated) {
            throw new IllegalArgumentException("Negation of BALL_MOVE_TO is not supported.");
        } else {
            if (minrange) {
                cp.post(new RegularInterval(positionZones[ball_idx], eventInterval, Automaton.A_NOTBSTAR_B(teams.length, zone_start, zone_end)));
            } else {
                CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n+1);
                int[] paddedArray = Automaton.pad(positionZones[ball_idx]);
                cp.post(new RegularInterval(paddedArray, paddedInterval, Automaton.PAD_APLUS_PADSTAR_BPLUS_PAD(teams.length, zone_start, zone_end)));
                cp.post(eq(start(eventInterval), start(paddedInterval)));
                cp.post(eq(end(eventInterval), minus(end(paddedInterval),3)));
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
                    cp.post(new RegularInterval(ball_inside_circle, eventInterval, Automaton.APLUS(1, Set.of(1))));
                }
            } else {
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
                        ball_inside_rectangle[f] = (bx >= rect[0] && bx <= rect[0] + rect[2] && by <= rect[1] && by >= rect[1]-rect[3]) ? 1 : 0;
                    }
                    cp.post(new RegularInterval(ball_inside_rectangle, eventInterval, Automaton.APLUS(1, Set.of(1))));
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

        if (IDENTIFIERS.get(player_from.name()) == null) {
            if (!isNegated) passer_id = element(possession, start(eventInterval));
            for (CPIntVar v : IDENTIFIERS.values()) {
                cp.post(neq(passer_id, v));
            }
            IDENTIFIERS.put(player_from.name, passer_id);
        } else {
            passer_id = IDENTIFIERS.get(player_from.name);
            if (!isNegated) cp.post(eq(passer_id, element(possession, start(eventInterval))));

        }

        if (IDENTIFIERS.get(player_to.name()) == null) {
            if (!isNegated) receiver_id = element(possession, end(eventInterval));
            for (CPIntVar v : IDENTIFIERS.values()) {
                cp.post(neq(receiver_id, v));
            }
            IDENTIFIERS.put(player_to.name, receiver_id);
        } else {
            receiver_id = IDENTIFIERS.get(player_to.name);
            if (!isNegated) cp.post(eq(receiver_id, element(possession, end(eventInterval))));
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
            cp.post(eq(passer_id, player_from.id()));
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
            cp.post(eq(receiver_id, player_to.id()));
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
        CPBoolVar passerIsLeft = isEq(sum(isPasserLeft),1);
        CPBoolVar receiverIsLeft = isEq(sum(isReceiverLeft),1);

        CPBoolVar[] isPasserRight = new CPBoolVar[rightTeamIds.size()];
        CPBoolVar[] isReceiverRight = new CPBoolVar[rightTeamIds.size()];
        c = 0;
        for (int i : rightTeamIds) {
            isPasserRight[c] = isEq(passer_id, i);
            isReceiverRight[c] = isEq(receiver_id, i);
            c++;
        }
        CPBoolVar passerIsRight = isEq(sum(isPasserRight),1);
        CPBoolVar receiverIsRight = isEq(sum(isReceiverRight),1);

        cp.post(eq(passerIsRight, receiverIsRight));
        cp.post(eq(passerIsLeft, receiverIsLeft));


        if (isNegated) {
            throw new IllegalArgumentException("Negation of PASS_TO is not supported.");
        } else {
            if (minrange) {
                cp.post(new RegularInterval(possession, eventInterval, Automaton.A_0STAR_B(teams.length, passersIds, receiversIds)));
            } else {
                CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n + 1);
                int[] paddedArray = Automaton.pad(possession);
                cp.post(new RegularInterval(paddedArray, paddedInterval, Automaton.PAD_APLUS_PADSTAR_BPLUS_PAD(teams.length, passersIds, receiversIds)));
                cp.post(eq(start(eventInterval), start(paddedInterval)));
                cp.post(eq(end(eventInterval), minus(end(paddedInterval), 3)));
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
                    cp.post(new RegularInterval(ball_inside_circle, eventInterval, Automaton.APLUS(teams.length, Set.of(1))));
                }
            } else {
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
                        ball_inside_rectangle[f] = (bx >= rect[0] && bx <= rect[0] + rect[2] && by <= rect[1] && by >= rect[1]-rect[3]) ? 1 : 0;
                    }
                    cp.post(new RegularInterval(ball_inside_rectangle, eventInterval, Automaton.APLUS(teams.length, Set.of(1))));
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


        Player player = (Player) subject;

        CPIntVar player_id = makeIntVar(cp, teams.length);

        if (IDENTIFIERS.get(player.name()) == null) {
            for (CPIntVar v : IDENTIFIERS.values()) {
                cp.post(neq(player_id, v));
            }
            IDENTIFIERS.put(player.name, player_id);
        } else {
            player_id = IDENTIFIERS.get(player.name);
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
                        cp.post(new RegularInterval(positionZones[pl_id], thisPlayerTrueInterval, Automaton.A_NOTBSTAR_B(teams.length, zone_start, zone_end)));
                    } else {
                        CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n + 1);
                        int[] paddedArray = Automaton.pad(positionZones[pl_id]);
                        cp.post(new RegularInterval(paddedArray, paddedInterval, Automaton.PAD_APLUS_PADSTAR_BPLUS_PAD(teams.length, zone_start, zone_end)));
                        cp.post(eq(start(thisPlayerTrueInterval), start(paddedInterval)));
                        cp.post(eq(end(thisPlayerTrueInterval), minus(end(paddedInterval), 3)));
                    }
                    //isThisPlayerVars[pl_id] = isEq(sum(isEq(player_id, pl_id), isEq(start(thisPlayerTrueInterval), start(eventInterval)), isEq(end(thisPlayerTrueInterval), end(eventInterval))), 3);
                    isThisPlayerVars[pl_id] = isEq(player_id, pl_id);
                    cp.post(implies(isThisPlayerVars[pl_id], isEq(start(thisPlayerTrueInterval), start(eventInterval))));
                    cp.post(implies(isThisPlayerVars[pl_id], isEq(end(thisPlayerTrueInterval), end(eventInterval))));

                } catch (InconsistencyException e) {
                    isThisPlayerVars[pl_id] = makeBoolVar(cp);
                    isThisPlayerVars[pl_id].fix(false);
                }
            }
            cp.post(eq(sum(Arrays.stream(isThisPlayerVars)
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
                        CPIntVar distx = mul(minus(element(positionBox_x_T[f], player_id), circle[1]), minus(element(positionBox_x_T[f], player_id), circle[1]));
                        CPIntVar disty = mul(minus(element(positionBox_y_T[f], player_id), circle[2]), minus(element(positionBox_y_T[f], player_id), circle[2]));
                        pl_inside_circle[f] = isLe(sum(distx, disty), circle[0]); //inside the circle

                    }
                    cp.post(new TrueInterval(pl_inside_circle, eventInterval));
                }
            } else {
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
                        CPBoolVar lower_bound_y = isGe(by, rect[1]-rect[3]);
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
                chosenIntervals[teamPlayer] = makeIntervalVar(cp, 0, n+1);

                if (IDENTIFIERS.get(player.name()) == null) {
                    player_id = makeIntVar(cp, teams.length);
                    for (CPIntVar v : IDENTIFIERS.values()) {
                        cp.post(neq(player_id, v));
                    }
                    IDENTIFIERS.put(player.name, player_id);
                } else {
                    player_id = IDENTIFIERS.get(player.name);
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
                    if (player.team().equals("left")) {
                        for (int i : rightTeamIds) {
                            player_id.remove(i);
                        }
                    } else if (player.team().equals("right")) {
                        for (int i : leftTeamIds) {
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
                    if (pl_id == 0){
                        continue;
                    }
                    CPIntervalVar thisPlayerInterval = makeIntervalVar(cp, false, 0, n+1);
                    try {
                        if (minrange) {
                            cp.post(new RegularInterval(positionZones[pl_id], thisPlayerInterval, Automaton.A_NOTBSTAR_B(teams.length, zone_start, zone_end)));
                        } else {
                            CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n+1);
                            int[] paddedArray = Automaton.pad(positionZones[pl_id]);
                            cp.post(new RegularInterval(paddedArray, paddedInterval, Automaton.PAD_APLUS_PADSTAR_BPLUS_PAD(teams.length, zone_start, zone_end)));
                            cp.post(eq(start(thisPlayerInterval), start(paddedInterval)));
                            cp.post(eq(end(thisPlayerInterval), minus(end(paddedInterval),3)));
                        }
                        isThisPlayerVars[pl_id] = isEq(sum(isEq(player_id, pl_id), isEq(startOr(chosenIntervals[teamPlayer],0), start(thisPlayerInterval)), isEq(endOr(chosenIntervals[teamPlayer], n), end(thisPlayerInterval))), 3);

                    } catch (InconsistencyException e) {
                        isThisPlayerVars[pl_id] = makeBoolVar(cp);
                        isThisPlayerVars[pl_id].fix(false);
                    }
                }

                cp.post(eq(sum(Arrays.stream(isThisPlayerVars)
                        .filter(Objects::nonNull)
                        .toArray(CPBoolVar[]::new)), 1));
                teamPlayer++;
            }

           cp.post(ge(sum(Arrays.stream(chosenIntervals).map(CPIntervalVar::status).toArray(CPIntVar[]::new)), k));

            CPIntVar[] starts = Arrays.stream(chosenIntervals).map(itv -> startOr(itv, 0)).toArray(CPIntVar[]::new);
            cp.post(eq(event_start,  max(starts)));
            CPIntVar[] ends = Arrays.stream(chosenIntervals).map(itv -> endOr(itv, n)).toArray(CPIntVar[]::new);
            cp.post(eq(event_end, minimum(ends)));

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
            } else {
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
            if (zones.length==0){
                throw new IllegalArgumentException("At least one zone must be specified for POSITION");
            }

            CPIntVar player_id = makeIntVar(cp, teams.length);

            if (IDENTIFIERS.get(player.name()) == null) {
                for (CPIntVar v : IDENTIFIERS.values()) {
                    cp.post(neq(player_id, v));
                }
                IDENTIFIERS.put(player.name, player_id);
            } else {
                player_id = IDENTIFIERS.get(player.name);
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
                for (int i = 0; i < positionZones.length; i++) {
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
                    if (isNegated){
                        int[] paddedArray = Automaton.pad(positionZones[pl_id], zones[0]);
                        int[] otherZones = java.util.stream.IntStream.rangeClosed(0, nbZones)
                                .filter(z -> Arrays.stream(zones).noneMatch(v -> v == z))
                                .toArray();
                        cp.post(new RegularInterval(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones,Arrays.stream(otherZones).boxed().collect(Collectors.toSet()))));
                    } else{
                        int[] paddedArray = Automaton.pad(positionZones[pl_id]);
                        cp.post(new RegularInterval(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones,Arrays.stream(zones).boxed().collect(Collectors.toSet()))));
                    }
                    cp.post(eq(start(thisPlayerTrueInterval), start(paddedInterval)));
                    cp.post(eq(end(thisPlayerTrueInterval), minus(end(paddedInterval), 3)));

                    isThisPlayerVars[pl_id] = isEq(player_id, pl_id);
                    cp.post(implies(isThisPlayerVars[pl_id], isEq(start(thisPlayerTrueInterval), start(eventInterval))));
                    cp.post(implies(isThisPlayerVars[pl_id], isEq(end(thisPlayerTrueInterval), end(eventInterval))));

                } catch (InconsistencyException e) {
                    isThisPlayerVars[pl_id] = makeBoolVar(cp);
                    isThisPlayerVars[pl_id].fix(false);
                }
            }
            cp.post(eq(sum(Arrays.stream(isThisPlayerVars)
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
                            CPIntVar distx = mul(minus(element(positionBox_x_T[f], player_id), circle[1]), minus(element(positionBox_x_T[f], player_id), circle[1]));
                            CPIntVar disty = mul(minus(element(positionBox_y_T[f], player_id), circle[2]), minus(element(positionBox_y_T[f], player_id), circle[2]));
                            pl_inside_circle[f] = isLe(sum(distx, disty), circle[0]); //inside the circle

                        }
                        cp.post(new TrueInterval(pl_inside_circle, eventInterval));
                    }
                } else {
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
                            CPBoolVar lower_bound_y = isGe(by, rect[1]-rect[3]);
                            pl_inside_rect[f] = isEq(sum(upper_bound_x, lower_bound_x, upper_bound_y, lower_bound_y), 4); //inside the rectangle
                        }
                        cp.post(new TrueInterval(pl_inside_rect, eventInterval));
                    }
                }
            }

        } else if (subject instanceof Team team) {

            int[] zonesAndK = (int[]) payload;
            int[] zones = Arrays.copyOf(zonesAndK, zonesAndK.length - 1);
            if (zones.length==0){
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
                chosenIntervals[teamPlayer] = makeIntervalVar(cp, 0, n+1);

                if (IDENTIFIERS.get(player.name()) == null) {
                    player_id = makeIntVar(cp, teams.length);
                    for (CPIntVar v : IDENTIFIERS.values()) {
                        cp.post(neq(player_id, v));
                    }
                    IDENTIFIERS.put(player.name, player_id);
                } else {
                    player_id = IDENTIFIERS.get(player.name);
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
                    if (player.team().equals("left")) {
                        for (int i : rightTeamIds) {
                            player_id.remove(i);
                        }
                    } else if (player.team().equals("right")) {
                        for (int i : leftTeamIds) {
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
                    if (pl_id == 0){
                        continue;
                    }
                    CPIntervalVar thisPlayerInterval = makeIntervalVar(cp, false, 0, n+1);
                    try {
                        CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n+1);

                        if (isNegated){
                            int[] paddedArray = Automaton.pad(positionZones[pl_id],zones[0]);
                            int[] otherZones = java.util.stream.IntStream.rangeClosed(0, nbZones)
                                    .filter(z -> Arrays.stream(zones).noneMatch(v -> v == z))
                                    .toArray();
                            cp.post(new RegularInterval(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones,Arrays.stream(otherZones).boxed().collect(Collectors.toSet()))));
                        } else{
                            int[] paddedArray = Automaton.pad(positionZones[pl_id]);
                            cp.post(new RegularInterval(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones,Arrays.stream(zones).boxed().collect(Collectors.toSet()))));
                        }
                        cp.post(eq(start(thisPlayerInterval), start(paddedInterval)));
                        cp.post(eq(end(thisPlayerInterval), minus(end(paddedInterval),3)));

                        isThisPlayerVars[pl_id] = isEq(sum(isEq(player_id, pl_id), isEq(startOr(chosenIntervals[teamPlayer],0), start(thisPlayerInterval)), isEq(endOr(chosenIntervals[teamPlayer], n), end(thisPlayerInterval))), 3);

                    } catch (InconsistencyException e) {
                        isThisPlayerVars[pl_id] = makeBoolVar(cp);
                        isThisPlayerVars[pl_id].fix(false);
                    }
                }

                cp.post(eq(sum(Arrays.stream(isThisPlayerVars)
                        .filter(Objects::nonNull)
                        .toArray(CPBoolVar[]::new)), 1));
                teamPlayer++;
            }

            cp.post(ge(sum(Arrays.stream(chosenIntervals).map(CPIntervalVar::status).toArray(CPIntVar[]::new)), k));

            CPIntVar[] starts = Arrays.stream(chosenIntervals).map(itv -> startOr(itv, 0)).toArray(CPIntVar[]::new);
            cp.post(eq(event_start,  max(starts)));
            CPIntVar[] ends = Arrays.stream(chosenIntervals).map(itv -> endOr(itv, n)).toArray(CPIntVar[]::new);
            cp.post(eq(event_end, minimum(ends)));



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
                } else {
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
            if (zones.length==0){
                throw new IllegalArgumentException("At least one zone must be specified for POSITION of the ball");
            }
            CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n+2);
            if (isNegated){
                int[] paddedArray = Automaton.pad(positionZones[ball_idx], zones[0]);
                int[] otherZones = java.util.stream.IntStream.rangeClosed(0, nbZones)
                        .filter(z -> Arrays.stream(zones).noneMatch(v -> v == z))
                        .toArray();
                cp.post(new RegularInterval(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones,Arrays.stream(otherZones).boxed().collect(Collectors.toSet()))));
            } else{
                int[] paddedArray = Automaton.pad(positionZones[ball_idx]);
                cp.post(new RegularInterval(paddedArray, paddedInterval, Automaton.NOTA_APLUS_NOTA(nbZones,Arrays.stream(zones).boxed().collect(Collectors.toSet()))));
            }
            cp.post(eq(event_start, start(paddedInterval)));
            cp.post(eq(event_end, minus(end(paddedInterval), 3)));

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
                        cp.post(new RegularInterval(ball_inside_circle, eventInterval, Automaton.APLUS(1, Set.of(1))));
                    }
                } else {
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
                            ball_inside_rectangle[f] = (bx >= rect[0] && bx <= rect[0] + rect[2] && by <= rect[1] && by >= rect[1]-rect[3]) ? 1 : 0;
                        }
                        cp.post(new RegularInterval(ball_inside_rectangle, eventInterval, Automaton.APLUS(1, Set.of(1))));
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
                                 ArrayList<ExtendedCPVar> extVars,  int[] teams, int[][] positionBox_x, int[][] positionBox_y) {

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

            if (IDENTIFIERS.get(player.name()) == null) {
                if (!isNegated) player_id = element(possession, start(eventInterval));
                for (CPIntVar v : IDENTIFIERS.values()) {
                    cp.post(neq(player_id, v));
                }
                IDENTIFIERS.put(player.name, player_id);
            } else {
                player_id = IDENTIFIERS.get(player.name);
                if (!isNegated) cp.post(eq(player_id, element(possession, start(eventInterval))));
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
                cp.post(eq(player_id, player.id()));
            } else if (player.team() != null) {
                if (player.team().equals("left")) {
                    for (int i : leftTeamIds) {
                        validIds.add(i);
                    }
                    for (int i : rightTeamIds) {
                        player_id.remove(i);
                    }
                } else if (player.team().equals("right")) {
                    for (int i : rightTeamIds) {
                        validIds.add(i);
                    }
                    for (int i : leftTeamIds) {
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

            CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n+2);
            if (isNegated){
                int[] paddedPossession = Automaton.pad(possession, validIds.iterator().next());
                Set<Integer> allPossessionValues = Arrays.stream(possession).boxed().collect(Collectors.toSet());
                Set<Integer> complementIds = allPossessionValues.stream()
                        .filter(v -> !validIds.contains(v))
                        .collect(Collectors.toSet());
                cp.post(new RegularInterval(paddedPossession, paddedInterval, Automaton.NOTA_APLUS_NOTA(teams.length, complementIds)));
            } else {
                int[] paddedPossession = Automaton.pad(possession);
                cp.post(new RegularInterval(paddedPossession, paddedInterval, Automaton.NOTA_APLUS_NOTA(teams.length, validIds)));

            }
            cp.post(eq(event_start, start(paddedInterval)));
            cp.post(eq(event_end, minus(end(paddedInterval), 3)));

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
                        cp.post(new RegularInterval(ball_inside_circle, eventInterval, Automaton.APLUS(teams.length, Set.of(1))));
                    }
                } else {
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
                            ball_inside_rectangle[f] = (bx >= rect[0] && bx <= rect[0] + rect[2] && by <= rect[1] && by >= rect[1]-rect[3]) ? 1 : 0;
                        }
                        cp.post(new RegularInterval(ball_inside_rectangle, eventInterval, Automaton.APLUS(teams.length, Set.of(1))));
                    }
                }
            }


        } else if (subject instanceof Team team) {

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
                chosenIntervals[teamPlayer] = makeIntervalVar(cp, 0, n+1);

                if (IDENTIFIERS.get(player.name()) == null) {
                    player_id = makeIntVar(cp, teams.length);
                    for (CPIntVar v : IDENTIFIERS.values()) {
                        cp.post(neq(player_id, v));
                    }
                    IDENTIFIERS.put(player.name, player_id);
                } else {
                    player_id = IDENTIFIERS.get(player.name);
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
                    if (player.team().equals("left")) {
                        for (int i : rightTeamIds) {
                            player_id.remove(i);
                        }
                    } else if (player.team().equals("right")) {
                        for (int i : leftTeamIds) {
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
                    if (pl_id == 0){
                        continue;
                    }
                    CPIntervalVar thisPlayerInterval = makeIntervalVar(cp, false, 0, n+1);
                    try {
                        CPIntervalVar paddedInterval = makeIntervalVar(cp, false, 0, n+1);
                        if (isNegated){
                            int[] paddedPossession = Automaton.pad(possession, pl_id);
                            Set<Integer> allPossessionValues = Arrays.stream(possession).boxed().collect(Collectors.toSet());
                            Set<Integer> complementIds = allPossessionValues.stream()
                                    .filter(v -> v != pl_id)
                                    .collect(Collectors.toSet());
                            cp.post(new RegularInterval(paddedPossession, paddedInterval, Automaton.NOTA_APLUS_NOTA(teams.length, complementIds)));
                        } else {
                            int[] paddedPossession = Automaton.pad(possession);
                            cp.post(new RegularInterval(paddedPossession, paddedInterval, Automaton.NOTA_APLUS_NOTA(teams.length, Set.of(pl_id))));

                        }
                        cp.post(eq(start(thisPlayerInterval), start(paddedInterval)));
                        cp.post(eq(end(thisPlayerInterval), minus(end(paddedInterval),3)));

                        isThisPlayerVars[pl_id] = isEq(sum(isEq(player_id, pl_id), isEq(startOr(chosenIntervals[teamPlayer],0), start(thisPlayerInterval)), isEq(endOr(chosenIntervals[teamPlayer], n), end(thisPlayerInterval))), 3);

                    } catch (InconsistencyException e) {
                        isThisPlayerVars[pl_id] = makeBoolVar(cp);
                        isThisPlayerVars[pl_id].fix(false);
                    }
                }

                cp.post(eq(sum(Arrays.stream(isThisPlayerVars)
                        .filter(Objects::nonNull)
                        .toArray(CPBoolVar[]::new)), 1));
                teamPlayer++;
            }

            cp.post(ge(sum(Arrays.stream(chosenIntervals).map(CPIntervalVar::status).toArray(CPIntVar[]::new)), 1));

            CPIntVar[] starts = Arrays.stream(chosenIntervals).map(itv -> startOr(itv, 0)).toArray(CPIntVar[]::new);
            cp.post(eq(event_start,  max(starts)));
            CPIntVar[] ends = Arrays.stream(chosenIntervals).map(itv -> endOr(itv, n)).toArray(CPIntVar[]::new);
            cp.post(eq(event_end, minimum(ends)));



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
                } else {
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
                    Result.ResultEvent child = parseSingleEvent(extVars, i);
                    if (child != null) ge.children.add(child);
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


