package org.main.sn.logic;

import org.main.sn.dsl.*;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;
import org.opencv.core.Mat;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.maxicp.cp.CPFactory.*;

public class Query {

    public List<String> apply(SelectExpr seq) {

        CPSolver cp = CPFactory.makeSolver();

        List<String> toPrint = new ArrayList<>();

        final List<Event> steps = seq.steps;
        final List<GameStateReconstructionInstance> matches = seq.matches;
        int total_duration = seq.duration;
        int total_start = seq.start;
        int total_end = seq.end;
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

        for (GameStateReconstructionInstance instance : matches) {

            Map<String, CPIntVar> IDENTIFIERS = new HashMap<>(); //Entity's name -> CP variable

            ArrayList<ExtendedCPVar> extVars = new ArrayList<>();
            ArrayList<CPIntVar> frames = new ArrayList<>();
            AtomicInteger counterVars = new AtomicInteger(-1);
            AtomicInteger counterEvent = new AtomicInteger(0);

            try {
                int[] teams = instance.teams;
                int n = instance.n;
                int ball_idx = instance.ball_idx;
                Possession p = new Possession(cp, instance);
                int[] possession = p.result;
                Map<Integer, List<Mat.Tuple2<Integer>>> possessionIntervalsByPlayer = p.possessionIntervalsByPlayer;
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

                for (Event event : steps) {
                    CPIntVar frame_start = CPFactory.makeIntVar(cp, n + 1);
                    CPIntVar frame_end = CPFactory.makeIntVar(cp, n + 1);

                    modelEvent(event, cp, counterEvent, frame_start, frame_end, ball_idx, n, positionZones, positionBox_x,
                            positionBox_y, total_circle, extVars, total_rectangle, counterVars, instance, teams,
                            IDENTIFIERS, total_xcenter, possession, total_ycenter, total_radius, total_xtop, total_ytop,
                            total_w, total_h, frames, possessionIntervalsByPlayer);
                }

                //total duration
                CPIntVar lastVar = extVars.getLast().var;
                CPIntVar firstVar = extVars.getFirst().var;

                if (total_duration != -1) {
                    cp.post(le(sum(lastVar, minus(firstVar)), total_duration));
                }
                if (total_start != -1) {
                    cp.post(ge(firstVar, total_start));
                }
                if (total_end != -1) {
                    cp.post(le(lastVar, total_end));
                }
            } catch (InconsistencyException e) {
                System.out.println("No solution found for instance " + instance.name);
                continue;
            }

            // ******
            // SEARCH
            // ******

            toPrint.add("instance : " + instance.name + "\n");

            CPIntVar[] selectedFrames = extVars.stream().map(ev -> ev.var).toArray(CPIntVar[]::new);

            DFSearch search = makeDfs(cp, Searches.firstFail(selectedFrames));
            if (searchMode == 0){
                search.solve();
            } else {
                search.solve(statistics -> statistics.numberOfSolutions() == searchMode);
            }

            AtomicInteger solutionCounter = new AtomicInteger(0);

            search.onSolution(() -> {
                int solnb = solutionCounter.getAndIncrement();
                toPrint.add("solution #" + solnb);
                toPrint.add("------------------\n");
                AtomicInteger i = new AtomicInteger();
                while (i.get() < extVars.size()) {
                    ExtendedCPVar ev = extVars.get(i.get());
                    if (ev.type.equals("AND_interval_start")) {

                        int frame_start = ev.var.min();
                        int frame_end = extVars.get(i.incrementAndGet()).var.min();
                        toPrint.add(" THE NEXT " + ev.subSz + " EVENTS ARE PART OF THE \"AND\" | frames: " + frame_start + " to " + frame_end);

                    } else if (ev.type.equals("OR_interval_start")) {
                        int frame_start = ev.var.min();
                        int frame_end = extVars.get(i.incrementAndGet()).var.min();
                        toPrint.add(" THE NEXT " + ev.subSz + " EVENTS ARE PART OF THE \"OR\" | frames: " + frame_start + " to " + frame_end);

                    } else {
                        onSolution(extVars, toPrint, i);
                    }
                    toPrint.add("\n");
                }
            });
            //SearchStatistics stats = search.solve();
            //System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
            //System.out.format("Statistics: %s\n", stats);
        }
        return toPrint;
    }


    // ##################################
    // HELPER METHODS FOR MODELING EVENTS
    // ##################################


    private void modelEvent(Event event, CPSolver cp, AtomicInteger counterEvent, CPIntVar frame_start, CPIntVar frame_end,
                            int ball_idx, int n, int[][] positionZones, int[][] positionBox_x,
                            int[][] positionBox_y, boolean total_circle, ArrayList<ExtendedCPVar> extVars,
                            boolean total_rectangle, AtomicInteger counterVars, GameStateReconstructionInstance instance, int[] teams,
                            Map<String, CPIntVar> IDENTIFIERS, int total_xcenter, int[] possession,
                            int total_ycenter, int total_radius, int total_xtop, int total_ytop,
                            int total_w, int total_h, ArrayList<CPIntVar> frames, Map<Integer, List<Mat.Tuple2<Integer>>> possessionIntervalsByPlayer) {


        if (event instanceof AndEvent andEvent) {
            //we use frame_start and frame_end to represent the AND event interval
            extVars.add(new ExtendedCPVar(
                    frame_start,
                    counterVars.incrementAndGet(),
                    "AND_interval_start",
                    counterEvent.get(),
                    andEvent.isNegated,
                    andEvent.children().size()));
            extVars.add(new ExtendedCPVar(
                    frame_end,
                    counterVars.incrementAndGet(),
                    "AND_interval_end",
                    counterEvent.get(),
                    andEvent.isNegated,
                    andEvent.children().size()));

            CPIntVar[] childrenStarts = new CPIntVar[andEvent.children().size()];
            CPIntVar[] childrenEnds = new CPIntVar[andEvent.children().size()];

            int childrenIdx = 0;
            for (Event children : andEvent.children()) {
                CPIntVar childFrameStart = CPFactory.makeIntVar(cp, n + 1);
                CPIntVar childFrameEnd = CPFactory.makeIntVar(cp, n + 1);
                childrenStarts[childrenIdx] = childFrameStart;
                childrenEnds[childrenIdx] = childFrameEnd;
                childrenIdx++;

                cp.post(ge(childFrameStart, frame_start));
                cp.post(le(childFrameEnd, frame_end));

                executeStep(children, cp, counterEvent, childFrameStart, childFrameEnd, ball_idx, n,
                        positionZones, positionBox_x, positionBox_y, total_circle, extVars, total_rectangle,
                        counterVars, instance, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                        total_radius, total_xtop, total_ytop, total_w, total_h, frames, true, possessionIntervalsByPlayer);
            }

            //define the interval of the AND event
            cp.post(eq(frame_start, max(childrenStarts)));
            cp.post(eq(frame_end, minimum(childrenEnds)));

        } else if (event instanceof OrEvent orEvent) { // or should return the union of intervals

            //we use frame_start and frame_end to represent the AND event interval
            extVars.add(new ExtendedCPVar(
                    frame_start,
                    counterVars.incrementAndGet(),
                    "OR_interval_start",
                    counterEvent.get(),
                    orEvent.isNegated,
                    orEvent.children().size()));
            extVars.add(new ExtendedCPVar(
                    frame_end,
                    counterVars.incrementAndGet(),
                    "OR_interval_end",
                    counterEvent.get(),
                    orEvent.isNegated,
                    orEvent.children().size()));

            CPIntVar[] childrenStarts = new CPIntVar[orEvent.children().size()];
            CPIntVar[] childrenEnds = new CPIntVar[orEvent.children().size()];

            int childrenIdx = 0;
            for (Event children : orEvent.children()) {
                CPIntVar childFrameStart = CPFactory.makeIntVar(cp, n + 1);
                CPIntVar childFrameEnd = CPFactory.makeIntVar(cp, n + 1);
                childrenStarts[childrenIdx] = childFrameStart;
                childrenEnds[childrenIdx] = childFrameEnd;
                childrenIdx++;

                cp.post(ge(childFrameStart, frame_start));
                cp.post(le(childFrameEnd, frame_end));

                executeStep(children, cp, counterEvent, childFrameStart, childFrameEnd, ball_idx, n,
                        positionZones, positionBox_x, positionBox_y, total_circle, extVars, total_rectangle,
                        counterVars, instance, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                        total_radius, total_xtop, total_ytop, total_w, total_h, frames, true, possessionIntervalsByPlayer);
            }

            //define the interval of the OR event
            cp.post(eq(frame_start, minimum(childrenStarts)));
            cp.post(eq(frame_end, max(childrenEnds)));

        } else {
            executeStep(event, cp, counterEvent, frame_start, frame_end, ball_idx, n,
                    positionZones, positionBox_x, positionBox_y, total_circle, extVars, total_rectangle,
                    counterVars, instance, teams, IDENTIFIERS, total_xcenter, possession, total_ycenter,
                    total_radius, total_xtop, total_ytop, total_w, total_h, frames, false, possessionIntervalsByPlayer);
        }
    }


    private void executeStep(Event event, CPSolver cp, AtomicInteger counterEvent, CPIntVar frame_start, CPIntVar frame_end,
                             int ball_idx, int n, int[][] positionZones, int[][] positionBox_x,
                             int[][] positionBox_y, boolean total_circle, ArrayList<ExtendedCPVar> extVars,
                             boolean total_rectangle, AtomicInteger counterVars, GameStateReconstructionInstance instance,
                             int[] teams, Map<String, CPIntVar> IDENTIFIERS, int total_xcenter, int[] possession,
                             int total_ycenter, int total_radius, int total_xtop, int total_ytop,
                             int total_w, int total_h, ArrayList<CPIntVar> frames, boolean isAndEvent, Map<Integer, List<Mat.Tuple2<Integer>>> possessionIntervalsByPlayer) {

        Action action = event.action();
        Entity subject = event.subject();
        int event_timeStart = event.timeStart;
        int event_timeEnd = event.timeEnd;
        int event_duration = event.duration;
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

        ExtendedCPVar frame_start_ext = new ExtendedCPVar(
                frame_start,
                counterVars.incrementAndGet(),
                action.name,
                counterEvent.get(),
                isNegated
        );

        ExtendedCPVar frame_end_ext = new ExtendedCPVar(
                frame_end,
                counterVars.incrementAndGet(),
                action.name,
                counterEvent.get(),
                isNegated
        );
        extVars.add(frame_start_ext);
        extVars.add(frame_end_ext);
        frames.add(frame_start);
        frames.add(frame_end);

        if (event_timeStart != -1) {
            cp.post(ge(frame_start, event_timeStart));
        }
        if (event_timeEnd != -1) {
            cp.post(le(frame_end, event_timeEnd));
        }
        if (event_duration != -1) {
            cp.post(le(sum(frame_end, minus(frame_start)), event_duration));
        }

        switch (action.name) {

            //BALL EVENTS

            case "BALL_MOVE_TO" ->
                    model_BALL_MOVE_TO(cp, counterEvent, isNegated, payload, frame_start, frame_end, ball_idx, n,
                            positionZones, positionBox_x, positionBox_y, event_circle, event_rectangle,
                            total_circle, total_rectangle, event_xcenter, event_ycenter, event_radius,
                            event_xtop, event_ytop, event_w, event_h, total_xcenter, total_ycenter,
                            total_radius, total_xtop, total_ytop, teams, total_w, total_h, frames, isAndEvent, minrange);

            //PLAYER EVENTS

            case "PASS_TO" -> model_PASS_TO(cp, counterEvent, isNegated, payload, frame_start, frame_end, ball_idx, n,
                    positionBox_x, positionBox_y, event_circle, event_rectangle, total_circle,
                    total_rectangle, event_xcenter, event_ycenter, event_radius, event_xtop,
                    event_ytop, event_w, event_h, total_xcenter, total_ycenter, total_radius,
                    total_xtop, total_ytop, total_w, total_h, frames, subject, instance, teams,
                    IDENTIFIERS, counterVars, action, extVars, isAndEvent);

            case "PLATER_MOVE_TO" ->
                    model_PLAYER_MOVE_TO(cp, counterEvent, isNegated, frame_start, frame_end, event_circle,
                            event_rectangle, total_circle, total_rectangle, event_xcenter, event_ycenter,
                            event_radius, event_xtop, event_ytop, event_w, event_h, total_xcenter,
                            total_ycenter, total_radius, total_xtop, total_ytop, total_w, total_h, frames,
                            subject, teams, positionBox_x, positionBox_y, positionZones, payload, n, IDENTIFIERS,
                            counterVars, action, extVars, isAndEvent, minrange);

            //TEAM EVENTS

            case "TEAM_MOVE_TO" -> model_TEAM_MOVE_TO(cp, counterEvent, isNegated, frame_start, frame_end, event_circle,
                    event_rectangle, total_circle, total_rectangle, event_xcenter, event_ycenter,
                    event_radius, event_xtop, event_ytop, event_w, event_h, total_xcenter,
                    total_ycenter, total_radius, total_xtop, total_ytop, total_w, total_h, frames,
                    subject, teams, positionBox_x, positionBox_y, positionZones, payload, n, IDENTIFIERS,
                    counterVars, action, extVars, isAndEvent, minrange);

            //TEAM + PLAYER EVENTS

            case "POSSESSION" -> model_POSSESSION(cp, counterEvent, isNegated, frame_start, frame_end, ball_idx, n,
                    possession, event_circle, event_rectangle, total_circle, total_rectangle,
                    event_xcenter, event_ycenter, event_radius, event_xtop, event_ytop, event_w,
                    event_h, total_xcenter, total_ycenter, total_radius, total_xtop, total_ytop,
                    total_w, total_h, frames, subject, IDENTIFIERS, counterVars, action, extVars,
                    teams, positionBox_x, positionBox_y, isAndEvent, possessionIntervalsByPlayer);

            // TEAM + PLAYER + BALL EVENTS

            case "POSITION" -> model_POSITION(cp, counterEvent, isNegated, frame_start, frame_end, event_circle,
                    event_rectangle, total_circle, total_rectangle, event_xcenter, event_ycenter, event_radius,
                    event_xtop, event_ytop, event_w, event_h, total_xcenter, total_ycenter, total_radius, total_xtop,
                    total_ytop, total_w, total_h, frames, subject, teams, positionBox_x, positionBox_y, positionZones,
                    payload, n, isAndEvent, IDENTIFIERS, counterVars, action, extVars, ball_idx);

        }
        counterEvent.incrementAndGet();
    }

    private void model_BALL_MOVE_TO(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, Object payload, CPIntVar frame_start,
                                    CPIntVar frame_end, int ball_idx, int n, int[][] positionZones, int[][] positionBox_x,
                                    int[][] positionBox_y, boolean event_circle, boolean event_rectangle, boolean total_circle,
                                    boolean total_rectangle, int event_xcenter, int event_ycenter, int event_radius,
                                    int event_xtop, int event_ytop, int event_w, int event_h, int total_xcenter,
                                    int total_ycenter, int total_radius, int total_xtop, int total_ytop, int[] teams,
                                    int total_w, int total_h, ArrayList<CPIntVar> frames, boolean isAndEvent, boolean minrange) {

        cp.post(le(frame_start, frame_end));
        if (counterEvent.get() != 0 && !isAndEvent) {
            //frame_end of the event before < frame_start of this event
            cp.post(le(frames.get(frames.size() - 3), frame_start));
        }
        //ball movement logic

        int zone_start = ((int[]) payload)[0];
        int zone_end = ((int[]) payload)[1];

        int[] ppos = positionZones[ball_idx];
        HashMap<Integer, List<Mat.Tuple2<Integer>>> ballIntervals = new HashMap<>();

        findMoveInterval(isNegated, n, Set.of(ball_idx), new int[][]{ppos}, zone_start, zone_end, ballIntervals, minrange);

        Set<Integer> notBall = new HashSet<>();
        for (int i = 0; i < teams.length; i++) {
            if (i == ball_idx) continue; // skip the ball id
            notBall.add(i);
        }

        if (isNegated) {
            cp.post(constraintCorrectInterval(frame_start, frame_end, ballIntervals, notBall));
        } else {
            cp.post(constraintCorrectInterval(frame_start, frame_end, ballIntervals, Set.of(ball_idx)));
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
                    CPBoolVar[] ball_inside_circle = makeBoolVarArray(cp, n); //true = 1, false = 0
                    for (int f = 0; f < n; f++) {
                        int dist_x = (int) Math.pow(positionBox_x[ball_idx][f] - circle[1], 2);
                        int dist_y = (int) Math.pow(positionBox_y[ball_idx][f] - circle[2], 2);

                        ball_inside_circle[f].fix(dist_x + dist_y <= circle[0]);
                    }
                    cp.post(new TrueInterval(ball_inside_circle, frame_start, frame_end));
                }
            } else {
                List<double[]> rectangles = new ArrayList<>();
                if (event_rectangle)
                    rectangles.add(new double[]{event_xtop, event_ytop, event_w, event_h});
                if (total_rectangle)
                    rectangles.add(new double[]{total_xtop, total_ytop, total_w, total_h});

                for (double[] rect : rectangles) {
                    CPBoolVar[] ball_inside_rectangle = makeBoolVarArray(cp, n);
                    for (int f = 0; f < n; f++) {
                        double dist_x = positionBox_x[ball_idx][f] - rect[0];
                        double dist_y = positionBox_y[ball_idx][f] - rect[1];
                        ball_inside_rectangle[f].fix(dist_x <= rect[2] && dist_y <= rect[3]);
                    }
                    cp.post(new TrueInterval(ball_inside_rectangle, frame_start, frame_end));
                }
            }
        }
    }

    public void model_PASS_TO(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, Object payload, CPIntVar frame_start,
                              CPIntVar frame_end, int ball_idx, int n, int[][] positionBox_x, int[][] positionBox_y,
                              boolean event_circle, boolean event_rectangle, boolean total_circle, boolean total_rectangle,
                              int event_xcenter, int event_ycenter, int event_radius, int event_xtop, int event_ytop,
                              int event_w, int event_h, int total_xcenter, int total_ycenter, int total_radius,
                              int total_xtop, int total_ytop, int total_w, int total_h, ArrayList<CPIntVar> frames,
                              Entity subject, GameStateReconstructionInstance instance, int[] teams, Map<String, CPIntVar> IDENTIFIERS,
                              AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, boolean isAndEvent) {


        Player player_from = (Player) subject;
        Player player_to = (Player) payload;
        cp.post(le(frame_start, frame_end));
        if (counterEvent.get() != 0 && !isAndEvent) {
            //frame_end of the event before < frame_start of this event
            cp.post(le(frames.get(frames.size() - 3), frame_start));
        }

        //pass logic
        Pass pass = new Pass(cp, instance);
        pass.apply(cp, instance);
        Hashtable<Pass.PlayerAtFrame, Pass.PlayerAtFrame> res = pass.result;
        int[] passersIds = res.keySet().stream()
                .sorted(Comparator.comparingInt(Pass.PlayerAtFrame::frameId))
                .mapToInt(Pass.PlayerAtFrame::playerId)
                .toArray();
        int[] receiversIds = res.values().stream()
                .sorted(Comparator.comparingInt(Pass.PlayerAtFrame::frameId))
                .mapToInt(Pass.PlayerAtFrame::playerId)
                .toArray();
        int[] passersFrames = res.keySet().stream()
                .sorted(Comparator.comparingInt(Pass.PlayerAtFrame::frameId))
                .mapToInt(Pass.PlayerAtFrame::frameId)
                .toArray();
        int[] receiversFrames = res.values().stream()
                .sorted(Comparator.comparingInt(Pass.PlayerAtFrame::frameId))
                .mapToInt(Pass.PlayerAtFrame::frameId)
                .toArray();
        if (receiversFrames.length == 0 || passersFrames.length == 0) {
            //no pass found in the data
            throw new InconsistencyException();
        }

        //left = 0, right = 1
        int[] passersTeam = Arrays.stream(passersIds).map(id -> teams[id]).toArray();
        int[] receiversTeam = Arrays.stream(receiversIds).map(id -> teams[id]).toArray();

        CPIntVar pass_index = makeIntVar(cp, res.size());
        CPIntVar passer_id;
        CPIntVar receiver_id;

        if (IDENTIFIERS.get(player_from.name()) == null) {
            passer_id = element(passersIds, pass_index);
            for (CPIntVar v : IDENTIFIERS.values()) {
                cp.post(neq(passer_id, v));
            }
            IDENTIFIERS.put(player_from.name, passer_id);
        } else {
            passer_id = IDENTIFIERS.get(player_from.name);
            cp.post(eq(passer_id, element(passersIds, pass_index)));
        }

        if (IDENTIFIERS.get(player_to.name()) == null) {
            receiver_id = element(receiversIds, pass_index);
            for (CPIntVar v : IDENTIFIERS.values()) {
                cp.post(neq(receiver_id, v));
            }
            IDENTIFIERS.put(player_to.name, receiver_id);
            IDENTIFIERS.put(player_to.name, receiver_id);
        } else {
            receiver_id = IDENTIFIERS.get(player_to.name);
            cp.post(eq(receiver_id, element(receiversIds, pass_index)));
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
        cp.post(eq(element(passersFrames, pass_index), frame_start));
        cp.post(eq(element(receiversFrames, pass_index), frame_end));

        String caseKey = (player_from.id() != null ? "ID" : player_from.team() != null ? "TEAM" : "NAME") +
                "_" + (player_to.id() != null ? "ID" : player_to.team() != null ? "TEAM" : "NAME");

        switch (caseKey) {
            case "ID_ID" -> { /* both by ID */
                if (isNegated) { // id(A) -> id(B) is not allowed
                    CPBoolVar passer_var = isEq(passer_id, player_from.id());
                    CPBoolVar receiver_var = isEq(receiver_id, player_to.id());
                    cp.post(neq(sum(passer_var, receiver_var), 2));
                } else {
                    cp.post(eq(passer_id, player_from.id()));
                    cp.post(eq(receiver_id, player_to.id()));
                }
            }
            case "ID_TEAM" -> { /* from ID, to team */
                int team = player_to.team().equals("left") ? 0 : 1;
                if (isNegated) { // id(A) -> team(B) is not allowed
                    CPBoolVar isPasser = isEq(element(receiversTeam, pass_index), team);
                    CPBoolVar isReceiver = isEq(passer_id, player_from.id());
                    cp.post(neq(sum(isPasser, isReceiver), 2));
                } else {
                    cp.post(eq(element(receiversTeam, pass_index), team));
                    cp.post(eq(passer_id, player_from.id()));
                }
            }
            case "ID_NAME" -> { /* from ID, to name */
                if (isNegated) { //id(A) cannot pass to name(B)
                    cp.post(neq(passer_id, player_from.id()));
                } else {
                    cp.post(eq(passer_id, player_from.id()));
                }
            }
            case "TEAM_ID" -> { /* from team, to ID */
                int team = player_from.team().equals("left") ? 0 : 1;
                if (isNegated) { // team(A) -> id(B) is not allowed
                    CPBoolVar isPasser = isEq(passer_id, player_to.id());
                    CPBoolVar isReceiver = isEq(element(passersTeam, pass_index), team);
                    cp.post(neq(sum(isPasser, isReceiver), 2));
                } else {
                    cp.post(eq(element(passersTeam, pass_index), team));
                    cp.post(eq(receiver_id, player_to.id()));
                }
            }
            case "TEAM_TEAM" -> { /* both by team; by definition if teams are different, it isn't possible */
                int team_from = player_from.team().equals("left") ? 0 : 1;
                int team_to = player_to.team().equals("left") ? 0 : 1;
                if (team_to != team_from) throw new InconsistencyException();
                if (isNegated) {
                    CPBoolVar isPasser = isEq(element(passersTeam, pass_index), team_from);
                    CPBoolVar isReceiver = isEq(element(receiversTeam, pass_index), team_to);
                    cp.post(neq(sum(isPasser, isReceiver), 2));
                } else {
                    cp.post(eq(element(passersTeam, pass_index), team_from));
                    cp.post(eq(element(receiversTeam, pass_index), team_to));
                }
            }
            case "TEAM_NAME" -> { /* from team, to name */
                int team = player_from.team().equals("left") ? 0 : 1;
                if (isNegated) {
                    cp.post(neq(element(passersTeam, pass_index), team));
                } else {
                    cp.post(eq(element(passersTeam, pass_index), team));
                }
            }
            case "NAME_ID" -> { /* from name, to ID */
                if (isNegated) cp.post(neq(receiver_id, player_to.id()));
                else cp.post(eq(receiver_id, player_to.id()));
            }
            case "NAME_TEAM" -> { /* from name, to team */
                int team = player_to.team().equals("left") ? 0 : 1;
                if (isNegated) {
                    cp.post(neq(element(receiversTeam, pass_index), team));
                } else
                    cp.post(eq(element(receiversTeam, pass_index), team));
            }
            case "NAME_NAME" -> { /* both by name */
                //every pass is correct
                if (isNegated) { //no pass is correct
                    if (passersIds.length != 0) throw new InconsistencyException();
                    //else do nothing, all passes are excluded
                }
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
                    CPBoolVar[] ball_inside_circle = makeBoolVarArray(cp, n);
                    for (int f = 0; f < n; f++) {
                        double dist_x = Math.pow(positionBox_x[ball_idx][f] - circle[1], 2);
                        double dist_y = Math.pow(positionBox_y[ball_idx][f] - circle[2], 2);
                        ball_inside_circle[f].fix(dist_x + dist_y <= circle[0]);
                    }
                    cp.post(new TrueInterval(ball_inside_circle, frame_start, frame_end));
                }
            } else {
                List<double[]> rectangles = new ArrayList<>();
                if (event_rectangle)
                    rectangles.add(new double[]{event_xtop, event_ytop, event_w, event_h});
                if (total_rectangle)
                    rectangles.add(new double[]{total_xtop, total_ytop, total_w, total_h});

                for (double[] rect : rectangles) {
                    CPBoolVar[] ball_inside_rectangle = makeBoolVarArray(cp, n);
                    for (int f = 0; f < n; f++) {
                        double dist_x = positionBox_x[ball_idx][f] - rect[0];
                        double dist_y = positionBox_y[ball_idx][f] - rect[1];
                        ball_inside_rectangle[f].fix(dist_x <= rect[2] && dist_y <= rect[3]);
                    }
                    cp.post(new TrueInterval(ball_inside_rectangle, frame_start, frame_end));
                }
            }
        }
    }

    public void model_PLAYER_MOVE_TO(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntVar frame_start,
                                     CPIntVar frame_end, boolean event_circle,
                                     boolean event_rectangle, boolean total_circle, boolean total_rectangle, int event_xcenter,
                                     int event_ycenter, int event_radius, int event_xtop, int event_ytop,
                                     int event_w, int event_h, int total_xcenter, int total_ycenter, double total_radius,
                                     int total_xtop, int total_ytop, int total_w, int total_h, ArrayList<CPIntVar> frames,
                                     Entity subject, int[] teams, int[][] positionBox_x, int[][] positionBox_y,
                                     int[][] positionZones, Object payload, int n, Map<String, CPIntVar> IDENTIFIERS,
                                     AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, boolean isAndEvent, boolean minrange) {


        Player player = (Player) subject;
        cp.post(le(frame_start, frame_end));
        if (counterEvent.get() != 0 && !isAndEvent) {
            //frame_end of the event before < frame_start of this event
            cp.post(le(frames.get(frames.size() - 3), frame_start));
        }

        int[][] player_pos = new int[teams.length][n]; // max size is the number of players

        CPIntVar player_id;

        if (IDENTIFIERS.get(player.name()) == null) {
            player_id = makeIntVar(cp, teams.length);
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
        Set<Integer> playerIds = new HashSet<>();
        Set<Integer> notPlayerIds = new HashSet<>();

        if (player.id() != null) {
            player_pos[player.id()] = positionZones[player.id()];
            cp.post(eq(player_id, player.id())); //should be useless
            playerIds.add(player.id());
            for (int i = 0; i < teams.length; i++) {
                if (i != player.id()) {
                    notPlayerIds.add(i);
                }
            }
        } else if (player.team() != null) {
            int team = player.team().equals("left") ? 0 : 1;
            for (int i = 0; i < teams.length; i++) {
                if (teams[i] == team) {
                    playerIds.add(i);
                    player_pos[i] = positionZones[i];
                } else {
                    notPlayerIds.add(i);
                }
            }
        } else {
            //all players
            //System.arraycopy(positionZones, 0, player_pos, 0, teams.length);
            for (int i = 0; i < positionZones.length; i++) {
                if (teams[i] == 0 || teams[i] == 1) {
                    playerIds.add(i);
                    player_pos[i] = positionZones[i];
                } else {
                    notPlayerIds.add(i);
                }
            }
        }
        //intervals in ppos such that zone_start and zone_end are at position 0 and position -1, but in between, it's only different zones.
        HashMap<Integer, List<Mat.Tuple2<Integer>>> intervals = new HashMap<>(); //playerId -> (last_frame_start_zone, first_frame_end_zone), ...

        //movement logic
        int zone_start = ((int[]) payload)[0];
        int zone_end = ((int[]) payload)[1];

        findMoveInterval(isNegated, n, playerIds, player_pos, zone_start, zone_end, intervals, minrange);

        if (isNegated) {
            cp.post(constraintCorrectInterval(frame_start, frame_end, intervals, notPlayerIds, player_id));
        } else {
            cp.post(constraintCorrectInterval(frame_start, frame_end, intervals, playerIds, player_id));
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
                    cp.post(new TrueInterval(pl_inside_circle, frame_start, frame_end));
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
                        CPIntVar distx = minus(element(positionBox_x_T[f], player_id), rect[0]);
                        CPIntVar disty = minus(element(positionBox_y_T[f], player_id), rect[1]);
                        //inside the rectangle -> equivalent to and(isLe(distx, rect[2]), isLe(disty, rect[3]))
                        pl_inside_rect[f] = not(isOr(isGe(distx, rect[2]), isGe(disty, rect[3])));
                    }
                    cp.post(new TrueInterval(pl_inside_rect, frame_start, frame_end));
                }
            }
        }
    }

    public void model_TEAM_MOVE_TO(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntVar frame_start,
                                   CPIntVar frame_end, boolean event_circle,
                                   boolean event_rectangle, boolean total_circle, boolean total_rectangle, int event_xcenter,
                                   int event_ycenter, int event_radius, int event_xtop, int event_ytop,
                                   int event_w, int event_h, int total_xcenter, int total_ycenter, double total_radius,
                                   int total_xtop, int total_ytop, int total_w, int total_h, ArrayList<CPIntVar> frames,
                                   Entity subject, int[] teams, int[][] positionBox_x, int[][] positionBox_y,
                                   int[][] positionZones, Object payload, int n, Map<String, CPIntVar> IDENTIFIERS,
                                   AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, boolean isAndEvent, boolean minrange) {


        Player player = (Player) subject;
        cp.post(le(frame_start, frame_end));
        if (counterEvent.get() != 0 && !isAndEvent) {
            //frame_end of the event before < frame_start of this event
            cp.post(le(frames.get(frames.size() - 3), frame_start));
        }

        int[][] player_pos = new int[teams.length][n]; // max size is the number of players

        CPIntVar player_id;

        if (IDENTIFIERS.get(player.name()) == null) {
            player_id = makeIntVar(cp, teams.length);
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
        Set<Integer> playerIds = new HashSet<>();
        Set<Integer> notPlayerIds = new HashSet<>();

        if (player.id() != null) {
            player_pos[player.id()] = positionZones[player.id()];
            cp.post(eq(player_id, player.id())); //should be useless
            playerIds.add(player.id());
            for (int i = 0; i < teams.length; i++) {
                if (i != player.id()) {
                    notPlayerIds.add(i);
                }
            }

        } else if (player.team() != null) {
            int team = player.team().equals("left") ? 0 : 1;
            for (int i = 0; i < teams.length; i++) {
                if (teams[i] == team) {
                    playerIds.add(i);
                    player_pos[i] = positionZones[i];
                } else {
                    notPlayerIds.add(i);
                }
            }
        } else {
            //all players
            //System.arraycopy(positionZones, 0, player_pos, 0, teams.length);
            for (int i = 0; i < positionZones.length; i++) {
                if (teams[i] == 0 || teams[i] == 1) {
                    playerIds.add(i);
                    player_pos[i] = positionZones[i];
                } else {
                    notPlayerIds.add(i);
                }
            }
        }
        //intervals in ppos such that zone_start and zone_end are at position 0 and position -1, but in between, it's only different zones.
        HashMap<Integer, List<Mat.Tuple2<Integer>>> intervals = new HashMap<>(); //playerId -> (last_frame_start_zone, first_frame_end_zone), ...

        //movement logic
        int zone_start = ((int[]) payload)[0];
        int zone_end = ((int[]) payload)[1];

        findMoveInterval(isNegated, n, playerIds, player_pos, zone_start, zone_end, intervals, minrange);
        if (isNegated) {
            cp.post(constraintCorrectInterval(frame_start, frame_end, intervals, notPlayerIds, player_id));
        } else {
            cp.post(constraintCorrectInterval(frame_start, frame_end, intervals, playerIds, player_id));
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
                    cp.post(new TrueInterval(pl_inside_circle, frame_start, frame_end));
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
                        CPIntVar distx = minus(element(positionBox_x_T[f], player_id), rect[0]);
                        CPIntVar disty = minus(element(positionBox_y_T[f], player_id), rect[1]);
                        //inside the rectangle -> equivalent to and(isLe(distx, rect[2]), isLe(disty, rect[3]))
                        pl_inside_rect[f] = not(isOr(isGe(distx, rect[2]), isGe(disty, rect[3])));
                    }
                    cp.post(new TrueInterval(pl_inside_rect, frame_start, frame_end));
                }
            }
        }
    }

    public void model_POSITION(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntVar frame_start,
                               CPIntVar frame_end, boolean event_circle, boolean event_rectangle, boolean total_circle,
                               boolean total_rectangle, int event_xcenter, int event_ycenter, int event_radius,
                               int event_xtop, int event_ytop, int event_w, int event_h, int total_xcenter, int total_ycenter,
                               double total_radius, int total_xtop, int total_ytop, int total_w, int total_h,
                               ArrayList<CPIntVar> frames, Entity subject, int[] teams, int[][] positionBox_x,
                               int[][] positionBox_y, int[][] positionZones, Object payload, int n, boolean isAndEvent,
                               Map<String, CPIntVar> IDENTIFIERS, AtomicInteger counterVars, Action action, ArrayList<ExtendedCPVar> extVars, int ball_idx) {

        cp.post(le(frame_start, frame_end));
        if (counterEvent.get() != 0 && !isAndEvent) {
            //frame_end of the event before < frame_start of this event
            cp.post(le(frames.get(frames.size() - 3), frame_start));
        }

        Set<Integer> totalPlayerIds = new HashSet<>();

        if (subject instanceof Player player) {
            int[] zones = (int[]) payload;

            int[][] player_pos = new int[teams.length][n]; // max size is the number of players

            CPIntVar player_id;

            if (IDENTIFIERS.get(player.name()) == null) {
                player_id = makeIntVar(cp, teams.length);
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

            Set<Integer> playerIds = new HashSet<>();
            Set<Integer> notPlayerIds = new HashSet<>();

            if (player.id() != null) {
                player_pos[player.id()] = positionZones[player.id()];
                cp.post(eq(player_id, player.id())); //should be useless
                playerIds.add(player.id());
                for (int i = 0; i < teams.length; i++) {
                    if (i != player.id()) {
                        notPlayerIds.add(i);
                    }
                }
            } else if (player.team() != null) {
                int team = player.team().equals("left") ? 0 : 1;
                for (int i = 0; i < teams.length; i++) {
                    if (teams[i] == team) {
                        playerIds.add(i);
                        player_pos[i] = positionZones[i];
                    } else {
                        notPlayerIds.add(i);
                    }
                }
            } else {
                //all players
                //System.arraycopy(positionZones, 0, player_pos, 0, teams.length);
                for (int i = 0; i < positionZones.length; i++) {
                    if (teams[i] == 0 || teams[i] == 1) {
                        playerIds.add(i);
                        player_pos[i] = positionZones[i];
                    } else {
                        notPlayerIds.add(i);
                    }
                }
            }
            //intervals in ppos such that zone_start and zone_end are at position 0 and position -1, but in between, it's only different zones.
            HashMap<Integer, List<Mat.Tuple2<Integer>>> intervals = new HashMap<>(); //playerId -> (last_frame_start_zone, first_frame_end_zone), ...

            findZoneInterval(isNegated, n, playerIds, player_pos, zones, intervals);

            if (isNegated) {
                cp.post(constraintCorrectInterval(frame_start, frame_end, intervals, notPlayerIds, player_id));
                totalPlayerIds.addAll(notPlayerIds);
            } else {
                cp.post(constraintCorrectInterval(frame_start, frame_end, intervals, playerIds, player_id));
                totalPlayerIds.addAll(playerIds);
            }

        } else if (subject instanceof Team team) {
            int[] zonesAndK = (int[]) payload;
            int[] zones = Arrays.copyOf(zonesAndK, zonesAndK.length - 1);
            int k = zonesAndK[zonesAndK.length-1];
            int[][] player_pos = new int[teams.length][n]; // max size is the number of players

            CPBoolVar[] playerInZones = makeBoolVarArray(cp, team.players().size());
            int count = 0;

            for (Player player : team.players()){

                CPIntVar player_id;

                if (IDENTIFIERS.get(player.name()) == null) {
                    player_id = makeIntVar(cp, teams.length);
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

                Set<Integer> playerIds = new HashSet<>();
                Set<Integer> notPlayerIds = new HashSet<>();

                if (player.id() != null) {
                    player_pos[player.id()] = positionZones[player.id()];
                    cp.post(eq(player_id, player.id())); //should be useless
                    playerIds.add(player.id());
                    for (int i = 0; i < teams.length; i++) {
                        if (i != player.id()) {
                            notPlayerIds.add(i);
                        }
                    }
                }
                else if (player.team() != null) {
                    int t = player.team().equals("left") ? 0 : 1;
                    for (int i = 0; i < teams.length; i++) {
                        if (teams[i] == t) {
                            playerIds.add(i);
                            player_pos[i] = positionZones[i];
                        } else {
                            notPlayerIds.add(i);
                        }
                    }
                } else {
                    //all players
                    //System.arraycopy(positionZones, 0, player_pos, 0, teams.length);
                    for (int i = 0; i < positionZones.length; i++) {
                        if (teams[i] == 0 || teams[i] == 1) {
                            playerIds.add(i);
                            player_pos[i] = positionZones[i];
                        } else {
                            notPlayerIds.add(i);
                        }
                    }
                }
                //intervals in ppos such that zone_start and zone_end are at position 0 and position -1, but in between, it's only different zones.
                HashMap<Integer, List<Mat.Tuple2<Integer>>> intervals = new HashMap<>(); //playerId -> (last_frame_start_zone, first_frame_end_zone), ...

                findZoneInterval(isNegated, n, playerIds, player_pos, zones, intervals);

                if (isNegated) {
                    playerInZones[count] = constraintCorrectInterval(frame_start, frame_end, intervals, notPlayerIds, player_id);
                    count++;
                    totalPlayerIds.addAll(notPlayerIds);
                } else {
                    playerInZones[count] = constraintCorrectInterval(frame_start, frame_end, intervals, playerIds, player_id);
                    count++;
                    totalPlayerIds.addAll(playerIds);
                }
            }
            cp.post(eq(sum(playerInZones),k));

        } else { //ball
            int[] zones = (int[]) payload;

            int[][] ball_pos = new int[teams.length][n]; // max size is the number of players

            Set<Integer> BallId = new HashSet<>();
            Set<Integer> NotBallId = new HashSet<>();

            for (int i = 0; i < positionZones.length; i++) {
                if (i == ball_idx) {
                    BallId.add(i);
                    ball_pos[i] = positionZones[i];
                } else {
                    NotBallId.add(i);
                }
            }
            //intervals in ppos such that zone_start and zone_end are at position 0 and position -1, but in between, it's only different zones.
            HashMap<Integer, List<Mat.Tuple2<Integer>>> intervals = new HashMap<>(); //playerId -> (last_frame_start_zone, first_frame_end_zone), ...

            findZoneInterval(isNegated, n, BallId, ball_pos, zones, intervals);

            if (isNegated) {
                cp.post(constraintCorrectInterval(frame_start, frame_end, intervals, NotBallId));
                totalPlayerIds.addAll(NotBallId);
            } else {
                cp.post(constraintCorrectInterval(frame_start, frame_end, intervals, BallId));
                totalPlayerIds.addAll(BallId);
            }
        }

        // spatial constraints
        if (event_circle || event_rectangle || total_circle || total_rectangle) {
            //TODO : need to change for the NOT
            if (event_circle || total_circle) {
                List<double[]> circles = new ArrayList<>();
                if (event_circle)
                    circles.add(new double[]{Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                if (total_circle)
                    circles.add(new double[]{Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                for (double[] circle : circles) {
                    CPBoolVar[] inside_circle = makeBoolVarArray(cp, n);
                    for (int playerId : totalPlayerIds) {
                        for (int f = 0; f < n; f++) {
                            int dist_x = (int) Math.pow(positionBox_x[playerId][f] - circle[1], 2);
                            int dist_y = (int) Math.pow(positionBox_y[playerId][f] - circle[2], 2);
                            inside_circle[f].fix(dist_x + dist_y <= circle[0]);
                        }
                        cp.post(new TrueInterval(inside_circle, frame_start, frame_end));
                    }
                }
            } else {
                List<double[]> rectangles = new ArrayList<>();
                if (event_rectangle)
                    rectangles.add(new double[]{event_xtop, event_ytop, event_w, event_h});
                if (total_rectangle)
                    rectangles.add(new double[]{total_xtop, total_ytop, total_w, total_h});

                for (double[] rect : rectangles) {
                    CPBoolVar[] inside_rectangle = makeBoolVarArray(cp, n);
                    for (int playerId : totalPlayerIds) {
                        for (int f = 0; f < n; f++) {
                            double dist_x = positionBox_x[playerId][f] - rect[0];
                            double dist_y = positionBox_y[playerId][f] - rect[1];
                            inside_rectangle[f].fix(dist_x <= rect[2] && dist_y <= rect[3]);
                        }
                        cp.post(new TrueInterval(inside_rectangle, frame_start, frame_end));
                    }
                }
            }
        }
    }

    public void model_POSSESSION(CPSolver cp, AtomicInteger counterEvent, boolean isNegated, CPIntVar frame_start,
                                 CPIntVar frame_end, int ball_idx, int n, int[] possession, boolean event_circle,
                                 boolean event_rectangle, boolean total_circle, boolean total_rectangle, int event_xcenter,
                                 int event_ycenter, int event_radius, int event_xtop, int event_ytop,
                                 int event_w, int event_h, int total_xcenter, int total_ycenter, int total_radius,
                                 int total_xtop, int total_ytop, int total_w, int total_h, ArrayList<CPIntVar> frames,
                                 Entity subject, Map<String, CPIntVar> IDENTIFIERS, AtomicInteger counterVars, Action action,
                                 ArrayList<ExtendedCPVar> extVars, int[] teams, int[][] positionBox_x, int[][] positionBox_y,
                                 boolean isAndEvent, Map<Integer, List<Mat.Tuple2<Integer>>> possessionIntervalsByPlayer) {

        cp.post(le(frame_start, frame_end));
        if (counterEvent.get() != 0 && !isAndEvent) {
            //frame_end of the event before < frame_start of this event
            cp.post(le(frames.get(frames.size() - 3), frame_start));
        }

        if (subject instanceof Player player) {

            CPIntVar player_id;

            if (IDENTIFIERS.get(player.name()) == null) {
                player_id = makeIntVar(cp, teams.length);
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

            //possession logic

            if (player.id() != null) {
                cp.post(eq(player_id, player.id()));
                int[] playerHasBall = new int[possession.length];
                for (int f = 0; f < possession.length; f++) {
                    playerHasBall[f] = possession[f] == player.id() ? 1 : 0;
                }
                if (isNegated) //the player with the id cannot have the ball
                    cp.post(new NoInBetween(playerHasBall, frame_start, frame_end, 0));
                else {
                    cp.post(new NoInBetween(playerHasBall, frame_start, frame_end, 1));
                }

            } else if (player.team() != null) {
                int team = player.team().equals("left") ? 0 : 1;
                Set<Integer> playerIds = new HashSet<>();
                Set<Integer> notPlayerIds = new HashSet<>();
                for (int i = 0; i < teams.length; i++) {
                    if (teams[i] == team) {
                        playerIds.add(i);
                    } else {
                        notPlayerIds.add(i);
                    }
                }

                if (isNegated) {
                    cp.post(constraintCorrectInterval(frame_start, frame_end, possessionIntervalsByPlayer, notPlayerIds, player_id));
                } else {
                    cp.post(constraintCorrectInterval(frame_start, frame_end, possessionIntervalsByPlayer, playerIds, player_id));
                }

            } else {
                Set<Integer> playerIds = new HashSet<>();
                Set<Integer> notPlayerIds = new HashSet<>();
                for (int i = 0; i < teams.length; i++) {
                    if (teams[i] == 0 | teams[i] == 1) {
                        playerIds.add(i);
                    } else {
                        notPlayerIds.add(i);
                    }
                }
                if (isNegated) {
                    cp.post(constraintCorrectInterval(frame_start, frame_end, possessionIntervalsByPlayer, notPlayerIds, player_id));
                } else {
                    cp.post(constraintCorrectInterval(frame_start, frame_end, possessionIntervalsByPlayer, playerIds, player_id));
                }
            }

        } else if (subject instanceof Team team) {

            CPBoolVar[] playerInZones = makeBoolVarArray(cp, team.players().size());
            int count = 0;

            for (Player player : team.players()) {

                CPIntVar player_id;

                if (IDENTIFIERS.get(player.name()) == null) {
                    player_id = makeIntVar(cp, teams.length);
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

                Set<Integer> playerIds = new HashSet<>();
                Set<Integer> notPlayerIds = new HashSet<>();

                if (player.id() != null) {
                    cp.post(eq(player_id, player.id())); //should be useless
                    playerIds.add(player.id());
                    for (int i = 0; i < teams.length; i++) {
                        if (i != player.id()) {
                            notPlayerIds.add(i);
                        }
                    }
                } else if (player.team() != null) {
                    int t = player.team().equals("left") ? 0 : 1;
                    for (int i = 0; i < teams.length; i++) {
                        if (teams[i] == t) {
                            playerIds.add(i);
                        } else {
                            notPlayerIds.add(i);
                        }
                    }
                } else {
                    //all players
                    //System.arraycopy(positionZones, 0, player_pos, 0, teams.length);
                    for (int i = 0; i < teams.length; i++) {
                        if (teams[i] == 0 || teams[i] == 1) {
                            playerIds.add(i);
                        } else {
                            notPlayerIds.add(i);
                        }
                    }
                }
                //intervals in ppos such that zone_start and zone_end are at position 0 and position -1, but in between, it's only different zones.
                if (isNegated) {
                    playerInZones[count] = constraintCorrectInterval(frame_start, frame_end, possessionIntervalsByPlayer, notPlayerIds, player_id);
                } else {
                    playerInZones[count] = constraintCorrectInterval(frame_start, frame_end, possessionIntervalsByPlayer, playerIds, player_id);
                }
                count++;
            }
            cp.post(eq(sum(playerInZones), 1));
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
                    CPBoolVar[] ball_inside_circle = makeBoolVarArray(cp, n);
                    for (int f = 0; f < n; f++) {
                        double dist_x = Math.pow(positionBox_x[ball_idx][f] - circle[1], 2);
                        double dist_y = Math.pow(positionBox_y[ball_idx][f] - circle[2], 2);
                        ball_inside_circle[f].fix(dist_x + dist_y <= circle[0]);
                    }
                    cp.post(new TrueInterval(ball_inside_circle, frame_start, frame_end));
                }
            } else {
                List<double[]> rectangles = new ArrayList<>();
                if (event_rectangle)
                    rectangles.add(new double[]{event_xtop, event_ytop, event_w, event_h});
                if (total_rectangle)
                    rectangles.add(new double[]{total_xtop, total_ytop, total_w, total_h});

                for (double[] rect : rectangles) {
                    CPBoolVar[] ball_inside_rectangle = makeBoolVarArray(cp, n);
                    for (int f = 0; f < n; f++) {
                        double dist_x = positionBox_x[ball_idx][f] - rect[0];
                        double dist_y = positionBox_y[ball_idx][f] - rect[1];
                        ball_inside_rectangle[f].fix(dist_x <= rect[2] && dist_y <= rect[3]);
                    }
                    cp.post(new TrueInterval(ball_inside_rectangle, frame_start, frame_end));
                }
            }
        }
    }

    public void onSolution(List<ExtendedCPVar> extVars, List<String> toPrint, AtomicInteger i) {
        ExtendedCPVar ev = extVars.get(i.get());
        if (Objects.equals(ev.type, "BALL_MOVE_TO")) {
            int frame_start = ev.var.min();
            int frame_end = extVars.get(i.incrementAndGet()).var.min();
            toPrint.add("EVENT # " + ev.event_idx + (ev.isNegated ? " BALL DOES NOT MOVE TO" : " MOVES TO"));
            toPrint.add(" BALL | frames: " + frame_start + " to " + frame_end);
            i.incrementAndGet();
        } else if (Objects.equals(ev.type, "PASS_TO")) {
            int frame_pass = ev.var.min();
            int frame_rec = extVars.get(i.incrementAndGet()).var.min();
            int passer_id = extVars.get(i.incrementAndGet()).var.min();
            int receiver_id = extVars.get(i.incrementAndGet()).var.min();
            toPrint.add("EVENT # " + ev.event_idx + (ev.isNegated ? " NOT PASS" : " PASS"));
            toPrint.add(" From player ID " + passer_id + " to player ID " + receiver_id +
                    " | frames: " + frame_pass + " to " + frame_rec);
            i.incrementAndGet();
        } else if (Objects.equals(ev.type, "POSSESSION")) {
            int frame_start = ev.var.min();
            int frame_end = extVars.get(i.incrementAndGet()).var.min();
            int player_id = extVars.get(i.incrementAndGet()).var.min();
            toPrint.add("EVENT # " + ev.event_idx + (ev.isNegated ? " DOES NOT HAVE BALL" : " HAS BALL"));
            toPrint.add(" Player ID " + player_id +
                    " | frames: " + frame_start + " to " + frame_end);
            i.incrementAndGet();
        } else if (Objects.equals(ev.type, "MOVE_TO")) {
            int frame_start = ev.var.min();
            int frame_end = extVars.get(i.incrementAndGet()).var.min();
            int player_id = extVars.get(i.incrementAndGet()).var.min();
            toPrint.add("EVENT # " + ev.event_idx + (ev.isNegated ? " DOES NOT MOVE TO" : " MOVES TO"));
            toPrint.add(" Player ID " + player_id +
                    " | frames: " + frame_start + " to " + frame_end);
            i.incrementAndGet();
        } else if (Objects.equals(ev.type, "IS_IN_ZONES")) {
            int frame_start = ev.var.min();
            int frame_end = extVars.get(i.incrementAndGet()).var.min();
            toPrint.add("EVENT # " + ev.event_idx + (ev.isNegated ? " IS NOT IN ZONES" : " IS IN ZONES"));
            toPrint.add(" TEAM  | frames: " + frame_start + " to " + frame_end);
            i.incrementAndGet();
        } else if (Objects.equals(ev.type, "POSITION")) {
            int frame_start = ev.var.min();
            int frame_end = extVars.get(i.incrementAndGet()).var.min();
            if (extVars.size()>i.get()+1){
                if (extVars.get(i.get()+1).type.contains("_player_id")) {
                    int player_id = extVars.get(i.get()+1).var.min();
                    toPrint.add("EVENT # " + ev.event_idx + (ev.isNegated ? " IS NOT IN ZONE" : " IS IN ZONE"));
                    toPrint.add(" Player ID " + player_id +
                            " | frames: " + frame_start + " to " + frame_end);
                    i.incrementAndGet();
                }
            } else {
                toPrint.add("EVENT # " + ev.event_idx + (ev.isNegated ? " IS NOT IN ZONE" : " IS IN ZONE"));
                toPrint.add(" TEAM  | frames: " + frame_start + " to " + frame_end);
                i.incrementAndGet();
            }

        }
    }

    // ****************** UTILS **********************

    private static CPBoolVar constraintCorrectInterval(CPIntVar frame_start, CPIntVar frame_end, Map<Integer, List<Mat.Tuple2<Integer>>> possessionIntervalsByPlayer, Set<Integer> playerIds, CPIntVar player_id) {
        List<CPBoolVar> intervalVars = new ArrayList<>();
        for (int pid : playerIds) {
            List<Mat.Tuple2<Integer>> intervals = possessionIntervalsByPlayer.get(pid);
            if (intervals != null) {
                for (Mat.Tuple2<Integer> interval : intervals) {
                    int start = interval.get_0();
                    int end = interval.get_1();
                    CPBoolVar isThisInterval = isEq(sum(isEq(frame_start, start), isEq(frame_end, end), isEq(player_id, pid)), 3);
                    intervalVars.add(isThisInterval);
                }
            }
        }
        return isEq(sum(intervalVars.toArray(new CPBoolVar[0])), 1);
    }

    private static CPBoolVar constraintCorrectInterval(CPIntVar frame_start, CPIntVar frame_end, Map<Integer, List<Mat.Tuple2<Integer>>> possessionIntervalsByPlayer, Set<Integer> playerIds) {
        List<CPBoolVar> intervalVars = new ArrayList<>();
        for (int pid : playerIds) {
            List<Mat.Tuple2<Integer>> intervals = possessionIntervalsByPlayer.get(pid);
            if (intervals != null) {
                for (Mat.Tuple2<Integer> interval : intervals) {
                    int start = interval.get_0();
                    int end = interval.get_1();
                    CPBoolVar isThisInterval = isEq(sum(isEq(frame_start, start), isEq(frame_end, end)), 2);
                    intervalVars.add(isThisInterval);
                }
            }
        }
        return isEq(sum(intervalVars.toArray(new CPBoolVar[0])), 1);
    }

    private static void findMoveInterval(boolean isNegated, int n, Set<Integer> playerIds, int[][] player_pos, int zone_start, int zone_end, HashMap<Integer, List<Mat.Tuple2<Integer>>> intervals, boolean minrange) {
        for (int pl : playerIds) {
            int[] ppos = player_pos[pl];
            List<Mat.Tuple2<Integer>> playerIntervals = new ArrayList<>();

            for (int f = 0; f < n; f++) {
                // Look for a starting point (in zone_start)
                if (ppos[f] == zone_start) {
                    int start_frame = f;

                    // Move past all consecutive zone_start frames
                    if (minrange) {
                        while (f < n && ppos[f] == zone_start) {
                            f++;
                        }
                    }
                    // Now look for zone_end, ensuring we don't encounter zone_start again
                    boolean foundEnd = false;
                    while (f < n) {
                        if (ppos[f] == zone_start) {
                            break; // Invalid interval - we returned to start zone
                        }
                        if (ppos[f] == zone_end) {
                            if (minrange) {
                                // Move past all consecutive zone_end frames
                                while (f < n && ppos[f] == zone_end) {
                                    f++;
                                }
                            }
                            playerIntervals.add(new Mat.Tuple2<>(start_frame, f));
                            foundEnd = true;
                            break;
                        }
                        f++;
                    }

                    if (!foundEnd) {
                        f--; // Backtrack if we didn't find zone_end
                    }
                }
            }

            if (!playerIntervals.isEmpty()) {
                intervals.put(pl, playerIntervals);
            }
        }
        if (intervals.isEmpty() && !isNegated) {
            throw new InconsistencyException();
        }
    }

    private static void findZoneInterval(boolean isNegated, int n, Set<Integer> playerIds, int[][] player_pos, int[] zones, HashMap<Integer, List<Mat.Tuple2<Integer>>> intervals) {
        Set<Integer> zoneSet = new HashSet<>(zones.length);
        for (int z : zones) zoneSet.add(z);
        for (int pl : playerIds) {
            int[] ppos = player_pos[pl];
            List<Mat.Tuple2<Integer>> playerIntervals = new ArrayList<>();

            for (int f = 0; f < n; f++) {
                if (zoneSet.contains(ppos[f])) {
                    int start_frame = f;

                    while (f < n && zoneSet.contains(ppos[f])) {
                        f++;
                    }
                    int endExclusive = f;
                    playerIntervals.add(new Mat.Tuple2<>(start_frame, endExclusive));
                }
            }
            if (!playerIntervals.isEmpty()) {
                intervals.put(pl, playerIntervals);
            }
        }
        if (intervals.isEmpty() && !isNegated) {
            throw new InconsistencyException();
        }
    }

    public static class ExtendedCPVar {
        CPIntVar var;
        int order;
        String type;
        int event_idx;
        boolean isNegated;
        int subSz;

        public ExtendedCPVar(CPIntVar var, int order, String type, int event_idx, boolean isNeg) {
            this.event_idx = event_idx;
            this.var = var;
            this.order = order;
            this.type = type;
            this.isNegated = isNeg;
            this.subSz = 0;
        }

        public ExtendedCPVar(CPIntVar var, int order, String type, int event_idx, boolean isNeg, int subSz) {
            this.event_idx = event_idx;
            this.var = var;
            this.order = order;
            this.type = type;
            this.isNegated = isNeg;
            this.subSz = subSz;
        }
    }
}


