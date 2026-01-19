package org.main.sn.logic;

import org.main.sn.dsl.*;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;
import org.opencv.core.Mat;

import java.util.*;

import static org.maxicp.cp.CPFactory.*;

public class Query {

    public void apply(Sequence seq) {

        CPSolver cp = CPFactory.makeSolver();

        ArrayList<ExtendedCPVar> extVars;
        ArrayList<CPIntVar> frames;


        Map<String, CPIntVar> IDENTIFIERS = new HashMap<>(); //Entity's name -> CP variable

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

        for (GameStateReconstructionInstance instance : matches) {
            extVars = new ArrayList<>();
            frames = new ArrayList<>();
            int counterVars = 0;
            int counterEvent = 0;

            try {
                int[] teams = instance.teams;
                int n = instance.n;
                int ball_idx = instance.ball_idx;
                Possession p = new Possession(cp, instance);
                int[] possession = p.result;
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
                    Object payload = action.payload;
                    boolean event_circle = (event_radius != -1);
                    boolean event_rectangle = (event_w != -1 && event_h != -1);
                    boolean isNegated = event.isNegated;

                    CPIntVar frame_start = CPFactory.makeIntVar(cp, n);
                    CPIntVar frame_end = CPFactory.makeIntVar(cp, n);

                    ExtendedCPVar frame_start_ext = new ExtendedCPVar(
                            frame_start,
                            counterVars++,
                            action.name,
                            counterEvent,
                            isNegated
                    );

                    ExtendedCPVar frame_end_ext = new ExtendedCPVar(
                            frame_end,
                            counterVars++,
                            action.name,
                            counterEvent,
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

                        case "BALL_MOVE_TO" -> {
                            cp.post(le(frame_start, frame_end));
                            if (counterEvent != 0) {
                                //frame_end of the event before < frame_start of this event
                                cp.post(lt(frames.get(frames.size() - 3), frame_start));
                            }

                            //ball movement logic

                            if (payload instanceof int[]) {
                                int zone_start = ((int[]) payload)[0];
                                int zone_end = ((int[]) payload)[0];
                                int[] ball_pos = positionZones[ball_idx];

                                if (isNegated) { // cannot have zone_start -> zone_end, every other combination is allowed
                                    CPBoolVar zone_start_var = isEq(element(ball_pos, frame_start), zone_start);
                                    CPBoolVar zone_end_var = isEq(element(ball_pos, frame_end), zone_end);
                                    cp.post(neq(sum(zone_start_var, zone_end_var), 2));
                                } else {
                                    cp.post(eq(element(ball_pos, frame_end), zone_end));
                                    cp.post(eq(element(ball_pos, frame_start), zone_start));
                                }


                            } else if (payload instanceof Integer) {
                                int zone = (int) payload;
                                int[] ball_pos = positionZones[ball_idx];
                                if (isNegated) { //cannot end up in zone
                                    cp.post(neq(element(ball_pos, frame_end), zone));
                                } else {
                                    cp.post(eq(element(ball_pos, frame_end), zone));
                                    cp.post(neq(element(ball_pos, frame_start), zone));
                                }

                            }

                            // spatial constraints

                            if (event_circle || event_rectangle || total_circle || total_rectangle) {
                                //TODO : add tolerance because of the rounding errors in casting to int
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

                        //PLAYER EVENTS

                        case "PASS_TO" -> {
                            Player player_from = (Player) subject;
                            Player player_to = (Player) payload;
                            cp.post(le(frame_start, frame_end));
                            if (counterEvent != 0) {
                                //frame_end of the event before < frame_start of this event
                                cp.post(lt(frames.get(frames.size() - 3), frame_start));
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
                            } else {
                                receiver_id = IDENTIFIERS.get(player_to.name);
                                cp.post(eq(receiver_id, element(receiversIds, pass_index)));
                            }

                            ExtendedCPVar passer_id_ext = new ExtendedCPVar(
                                    passer_id,
                                    counterVars++,
                                    action.name + "_passer_id",
                                    counterEvent,
                                    isNegated
                            );
                            ExtendedCPVar rec_index_ext = new ExtendedCPVar(
                                    receiver_id,
                                    counterVars++,
                                    action.name + "_receiver_id",
                                    counterEvent,
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
                                //TODO : add tolerance because of the rounding errors in casting to int
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
                        case "HAS_BALL" -> {
                            Player player = (Player) subject;
                            cp.post(le(frame_start, frame_end));
                            if (counterEvent != 0) {
                                //frame_end of the event before < frame_start of this event
                                cp.post(lt(frames.get(frames.size() - 3), frame_start));
                            }

                            CPIntVar player_id;

                            if (IDENTIFIERS.get(player.name()) == null) {
                                player_id = makeIntVar(cp, teams.length);
                                IDENTIFIERS.put(player.name, player_id);
                            } else {
                                player_id = IDENTIFIERS.get(player.name);
                            }

                            ExtendedCPVar player_id_ext = new ExtendedCPVar(
                                    player_id,
                                    counterVars++,
                                    action.name + "_player_id",
                                    counterEvent,
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
                                List<CPBoolVar> playerVars = new ArrayList<>();
                                for (int i = 0; i < teams.length; i++) {
                                    if (teams[i] == team) {
                                        playerIds.add(i);
                                        playerVars.add(isEq(player_id, i));
                                    }
                                }
                                cp.post(or(playerVars.toArray(new CPBoolVar[0]))); //the player must be in the team
                                int[] teamHasBall = new int[possession.length];
                                for (int f = 0; f < possession.length; f++) {
                                    teamHasBall[f] = playerIds.contains(possession[f]) ? 1 : 0;
                                }
                                if (isNegated) { //no player of the team can have the ball
                                    cp.post(new NoInBetween(teamHasBall, frame_start, frame_end, 0));
                                } else {
                                    cp.post(new NoInBetween(teamHasBall, frame_start, frame_end, 1));
                                }

                            } else {
                                //the player with the ball must appear in possession at frame_start and frame_end
                                int[] someoneHasBall = new int[possession.length];
                                for (int i = 0; i < possession.length; i++) {
                                    someoneHasBall[i] = possession[i] != -1 ? 1 : 0;
                                }
                                if (isNegated) { //no player can have the ball
                                    cp.post(new NoInBetween(someoneHasBall, frame_start, frame_end, 0));
                                } else {
                                    cp.post(new NoInBetween(someoneHasBall, frame_start, frame_end, 1));
                                }
                            }

                            if (event_circle || event_rectangle || total_circle || total_rectangle) {
                                //TODO : add tolerance because of the rounding errors in casting to int
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
                        case "MOVE_TO" -> {
                            Player player = (Player) subject;
                            cp.post(le(frame_start, frame_end));
                            if (counterEvent != 0) {
                                //frame_end of the event before < frame_start of this event
                                cp.post(lt(frames.get(frames.size() - 3), frame_start));
                            }

                            int[][] player_pos = new int[teams.length][n]; // max size is the number of players
                            for (int[] playerPo : player_pos) {
                                Arrays.fill(playerPo, -1);
                            }

                            CPIntVar player_id = makeIntVar(cp, teams.length);

                            if (player.id() != null) {
                                player_pos[0] = positionZones[player.id()];
                                cp.post(eq(player_id, player.id())); //should be useless

                            } else if (player.team() != null) {
                                int team = player.team().equals("left") ? 0 : 1;
                                List<Integer> playerIds = new ArrayList<>();
                                for (int i = 0; i < teams.length; i++) {
                                    if (teams[i] == team) {
                                        playerIds.add(i);
                                    }
                                }
                                for (int i = 0; i < playerIds.size(); i++) {
                                    player_pos[i] = positionZones[playerIds.get(i)];
                                }
                            } else {
                                //all players
                                if (teams.length >= 0) System.arraycopy(positionZones, 0, player_pos, 0, teams.length);
                            }

                            //intervals in ppos such that zone_start and zone_end are at position 0 and position -1, but in between, it's only different zones.
                            HashMap<Integer, List<Mat.Tuple2<Integer>>> intervals = new HashMap<>(); //playerId -> (last_frame_start_zone, first_frame_end_zone), ...

                            //movement logic
                            if (payload instanceof int[]) {
                                int zone_start = ((int[]) payload)[0];
                                int zone_end = ((int[]) payload)[0];

                                for (int pl = 0; pl < player_pos.length; pl++) {
                                    int[] ppos = player_pos[pl];
                                    List<Mat.Tuple2<Integer>> playerIntervals = new ArrayList<>();

                                    for (int f = 0; f < n; f++) {
                                        // Look for a starting point (in zone_start)
                                        if (ppos[f] == zone_start) {
                                            int start_frame = f;

                                            // Move past all consecutive zone_start frames
                                            while (f < n && ppos[f] == zone_start) {
                                                start_frame = f; // Keep updating to get the last frame in zone_start
                                                f++;
                                            }

                                            // Now look for zone_end, ensuring we don't encounter zone_start again
                                            boolean foundEnd = false;
                                            while (f < n) {
                                                if (ppos[f] == zone_start) {
                                                    break; // Invalid interval - we returned to start zone
                                                }
                                                if (ppos[f] == zone_end) {
                                                    // Found the first frame in zone_end
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
                                if (intervals.isEmpty()) {
                                    throw new InconsistencyException();
                                }
                                List<CPBoolVar> intervalSatisfied = new ArrayList<>();

                                for (Map.Entry<Integer, List<Mat.Tuple2<Integer>>> entry : intervals.entrySet()) {
                                    int playerId = entry.getKey();
                                    List<Mat.Tuple2<Integer>> playerIntervals = entry.getValue();

                                    // Create boolean for each interval of this player
                                    for (Mat.Tuple2<Integer> interval : playerIntervals) {
                                        int start = interval.get_0();
                                        int end = interval.get_1();

                                        //CPBoolVar isThisInterval = and(isEq(frame_start, start),isEq(frame_end, end));
                                        CPBoolVar isThisInterval = not(
                                                isOr(
                                                        not(isEq(frame_start, start)),
                                                        not(isEq(frame_end, end))
                                                )
                                        );
                                        CPBoolVar isThisPlayer = isEq(player_id, playerId);
                                        cp.post(eq(isThisInterval, isThisPlayer)); //if the interval is satisfied, then it's this player

                                        intervalSatisfied.add(isThisInterval);
                                    }

                                }

                                if (isNegated) {
                                    //no players can move from zone_start to zone_end at frame_start to frame_end
                                    //TODO : not sure this is actually correct
                                    cp.post(eq(sum((intervalSatisfied.toArray(new CPBoolVar[0]))), 0));
                                } else {
                                    //at least one of the intervals must be satisfied for at least one player
                                    cp.post(or(intervalSatisfied.toArray(new CPBoolVar[0])));
                                }


                            } else if (payload instanceof Integer) {
                                int zone = (int) payload;
                                List<Integer> entries = new ArrayList<>();
                                List<CPBoolVar> entryVars = new ArrayList<>();
                                for (int[] ppos : player_pos) {
                                    for (int i = 1; i < ppos.length; i++) {
                                        if (ppos[i] == zone && ppos[i - 1] != zone) {
                                            entries.add(i);
                                        }
                                    }

                                    for (int entry_frame : entries) {
                                        CPBoolVar isEntryInInterval = not(
                                                isOr(
                                                        isGe(frame_start, entry_frame),
                                                        isLe(frame_end, entry_frame)
                                                )
                                        );
                                        entryVars.add(isEntryInInterval);
                                    }
                                }
                                if (isNegated) {
                                    cp.post(eq(sum(entryVars.toArray(new CPBoolVar[0])), 0)); //no player can enter the zone in the interval
                                } else {
                                    cp.post(or(entryVars.toArray(new CPBoolVar[0]))); //at least one player enters the zone in the interval
                                }
                            }

                            // spatial constraints
                            if (event_circle || event_rectangle || total_circle || total_rectangle) {
                                //TODO : add tolerance because of the rounding errors in casting to int

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

                        //TEAM EVENTS

                        case "IS_IN_ZONES" -> {
                            //TODO : not take into account the GKs?
                            Team team = (Team) subject;
                            int team_int = team.name().equals("left") ? 0 : 1;
                            int[] zones = (int[]) payload;
                            cp.post(le(frame_start, frame_end));
                            if (counterEvent != 0) {
                                //frame_end of the event before < frame_start of this event
                                cp.post(lt(frames.get(frames.size() - 3), frame_start));
                            }

                            //is in zone logic

                            Set<Integer> playerIds = new HashSet<>();
                            for (int i = 0; i < teams.length; i++) {
                                if (teams[i] == team_int) {
                                    playerIds.add(i);
                                }
                            }

                            int[] areAllPlayersInZones = new int[playerIds.size()];
                            for (int f = 0; f < n; f++) {
                                boolean isOnePlayerNotinZone = false;
                                for (int playerId : playerIds) {
                                    int finalF = f;
                                    if (Arrays.stream(zones).noneMatch(z -> z == positionZones[playerId][finalF])) {
                                        isOnePlayerNotinZone = true;
                                    }
                                }
                                areAllPlayersInZones[f] = isOnePlayerNotinZone ? 0 : 1;
                            }
                            if (isNegated) {//at least one player of the team is NOT in the zones
                                cp.post(new NoInBetween(areAllPlayersInZones, frame_start, frame_end, 1));
                            } else {//all players of the team must be in the zones
                                cp.post(new NoInBetween(areAllPlayersInZones, frame_start, frame_end, 0));
                            }

                            // spatial constraints
                            if (event_circle || event_rectangle || total_circle || total_rectangle) {
                                //TODO : add tolerance because of the rounding errors in casting to int
                                if (event_circle || total_circle) {
                                    List<double[]> circles = new ArrayList<>();
                                    if (event_circle)
                                        circles.add(new double[]{Math.pow(event_radius, 2), event_xcenter, event_ycenter});
                                    if (total_circle)
                                        circles.add(new double[]{Math.pow(total_radius, 2), total_xcenter, total_ycenter});

                                    for (double[] circle : circles) {
                                        CPBoolVar[] inside_circle = makeBoolVarArray(cp, n);
                                        for (int playerId : playerIds) {
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
                                        for (int playerId : playerIds) {
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
                        //TODO: formation logic (is it actually relevant?)

                        //                    case "FORMATION" -> {
                        //                        Team team = (Team) subject;
                        //                        Formation formation = (Formation) payload;
                        //                        cp.post(le(frame_start, frame_end));
                        //                        if (counterEvent != 0) {
                        //                            //frame_end of the event before < frame_start of this event
                        //                            cp.post(lt(extVars.get(extVars.size() - 3).var, frame_start));
                        //                        }
                        //
                        //
                        //                    }
                    }
                    counterEvent++;
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
                System.out.println("Inconsistency detected during modeling: " + e.getMessage());
                continue;
            }

            // ******
            // SEARCH
            // ******

            CPIntVar[] selectedFrames = extVars.stream().map(ev -> ev.var).toArray(CPIntVar[]::new);

            DFSearch search = makeDfs(cp, Searches.firstFail(selectedFrames));
            ArrayList<ExtendedCPVar> finalExtVars = extVars;
            search.onSolution(() -> {
                for (int i = 0; i < finalExtVars.size(); i++) {
                    ExtendedCPVar ev = finalExtVars.get(i);
                    if (Objects.equals(ev.type, "PASS_TO")) {
                        int frame_pass = ev.var.min();
                        int frame_rec = finalExtVars.get(i + 1).var.min();
                        int passer_id = finalExtVars.get(i + 2).var.min();
                        int receiver_id = finalExtVars.get(i + 3).var.min();
                        System.out.println("EVENT # " + ev.event_idx + (ev.isNegated ? " NOT PASS" : " PASS"));
                        System.out.println(" From player ID " + passer_id + " to player ID " + receiver_id +
                                " | frames: " + frame_pass + " to " + frame_rec);
                        i += 3;
                    }
                    else if (Objects.equals(ev.type, "HAS_BALL")) {
                        int frame_start = ev.var.min();
                        int frame_end = finalExtVars.get(i + 1).var.min();
                        int player_id = finalExtVars.get(i + 2).var.min();
                        System.out.println("EVENT # " + ev.event_idx + (ev.isNegated ? " DOES NOT HAVE BALL" : " HAS BALL"));
                        System.out.println(" Player ID " + player_id +
                                " | frames: " + frame_start + " to " + frame_end);
                        i += 2;
                    }
                    else if (Objects.equals(ev.type, "MOVE_TO")) {
                        int frame_start = ev.var.min();
                        int frame_end = finalExtVars.get(i + 1).var.min();
                        int player_id = finalExtVars.get(i + 2).var.min();
                        System.out.println("EVENT # " + ev.event_idx + (ev.isNegated ? " DOES NOT MOVE TO" : " MOVES TO"));
                        System.out.println(" Player ID " + player_id +
                                " | frames: " + frame_start + " to " + frame_end);
                        i += 2;
                    }
                }
                System.out.println("-------------------");
            });
            SearchStatistics stats = search.solve();
            System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
            System.out.format("Statistics: %s\n", stats);
        }
    }


    static class ExtendedCPVar {
        CPIntVar var;
        int order;
        String type;
        int event_idx;
        boolean isNegated;

        public ExtendedCPVar(CPIntVar var, int order, String type, int event_idx, boolean isNeg) {
            this.event_idx = event_idx;
            this.var = var;
            this.order = order;
            this.type = type;
            this.isNegated = isNeg;
        }
    }
}


