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

import java.util.*;

import static org.maxicp.cp.CPFactory.*;

public class Query {

    public void apply(Sequence seq) {

        ArrayList<ExtendedCPVar> extVars = new ArrayList<>();
        CPSolver cp = CPFactory.makeSolver();

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
                    GameStateReconstructionInstance.FrameData framedata = frameData.get(f+1);
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

                int counterEvent = 0;
                int counterVars = 0;

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

                    CPIntVar frame_start = CPFactory.makeIntVar(cp, n);
                    CPIntVar frame_end = CPFactory.makeIntVar(cp, n);

                    ExtendedCPVar frame_start_ext = new ExtendedCPVar(
                            frame_start,
                            counterVars++,
                            action.name,
                            counterEvent
                    );

                    ExtendedCPVar frame_end_ext = new ExtendedCPVar(
                            frame_end,
                            counterVars++,
                            action.name,
                            counterEvent
                    );
                    extVars.add(frame_start_ext);
                    extVars.add(frame_end_ext);

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
                                cp.post(le(extVars.get(extVars.size() - 3).var, frame_start));
                            }
                            counterEvent++;

                            //ball movement logic

                            if (payload instanceof int[]) {
                                int zone_start = ((int[]) payload)[0];
                                int zone_end = ((int[]) payload)[0];
                                int[] ball_pos = positionZones[ball_idx];
                                cp.post(eq(element(ball_pos, frame_end), zone_end));
                                cp.post(eq(element(ball_pos, frame_start), zone_start));

                            } else if (payload instanceof Integer) {
                                int zone = (int) payload;
                                int[] ball_pos = positionZones[ball_idx];
                                cp.post(eq(element(ball_pos, frame_end), zone));
                                cp.post(neq(element(ball_pos, frame_start), zone));
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
                                cp.post(le(extVars.get(extVars.size() - 3).var, frame_start));
                            }
                            counterEvent++;

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
                            CPIntVar passer_id = element(passersIds, pass_index);
                            CPIntVar receiver_id = element(receiversIds, pass_index);
                            cp.post(eq(element(passersFrames, pass_index), frame_start));
                            cp.post(eq(element(receiversFrames, pass_index), frame_end));

                            String caseKey = (player_from.id() != null ? "ID" : player_from.team() != null ? "TEAM" : "NAME") +
                                    "_" + (player_to.id() != null ? "ID" : player_to.team() != null ? "TEAM" : "NAME");

                            switch (caseKey) {
                                case "ID_ID" -> { /* both by ID */
                                    cp.post(eq(passer_id, player_from.id()));
                                    cp.post(eq(receiver_id, player_to.id()));
                                }
                                case "ID_TEAM" -> { /* from ID, to team */
                                    int team = player_to.team().equals("left") ? 0 : 1;
                                    cp.post(eq(element(receiversTeam, pass_index), team));
                                    cp.post(eq(passer_id, player_from.id()));
                                }
                                case "ID_NAME" -> { /* from ID, to name */
                                    cp.post(eq(passer_id, player_from.id()));
                                }
                                case "TEAM_ID" -> { /* from team, to ID */
                                    int team = player_from.team().equals("left") ? 0 : 1;
                                    cp.post(eq(element(passersTeam, pass_index), team));
                                    cp.post(eq(receiver_id, player_to.id()));
                                }
                                case "TEAM_TEAM" -> { /* both by team */
                                    int team_from = player_from.team().equals("left") ? 0 : 1;
                                    int team_to = player_to.team().equals("left") ? 0 : 1;
                                    cp.post(eq(element(passersTeam, pass_index), team_from));
                                    cp.post(eq(element(receiversTeam, pass_index), team_to));
                                }
                                case "TEAM_NAME" -> { /* from team, to name */
                                    int team = player_from.team().equals("left") ? 0 : 1;
                                    cp.post(eq(element(passersTeam, pass_index), team));
                                }
                                case "NAME_ID" -> { /* from name, to ID */
                                    cp.post(eq(receiver_id, player_to.id()));
                                }
                                case "NAME_TEAM" -> { /* from name, to team */
                                    int team = player_to.team().equals("left") ? 0 : 1;
                                    cp.post(eq(element(receiversTeam, pass_index), team));
                                }
                                case "NAME_NAME" -> { /* both by name */
                                    //every pass is correct
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
                                cp.post(le(extVars.get(extVars.size() - 3).var, frame_start));
                            }
                            counterEvent++;

                            //possession logic

                            if (player.id() != null) {
                                cp.post(eq(element(possession, frame_start), player.id()));
                                cp.post(eq(element(possession, frame_end), player.id()));

                            } else if (player.team() != null) {
                                int team = player.team().equals("left") ? 0 : 1;
                                List<Integer> playerIds = new ArrayList<>();
                                for (int i = 0; i < teams.length; i++) {
                                    if (teams[i] == team) {
                                        playerIds.add(i);
                                    }
                                }
                                CPBoolVar[] isInTeamStart = new CPBoolVar[playerIds.size()];
                                CPBoolVar[] isInTeamEnd = new CPBoolVar[playerIds.size()];
                                for (int i = 0; i < playerIds.size(); i++) {
                                    isInTeamStart[i] = isEq(element(possession, frame_start), playerIds.get(i));
                                    isInTeamEnd[i] = isEq(element(possession, frame_end), playerIds.get(i));
                                }
                                cp.post(or(isInTeamStart));
                                cp.post(or(isInTeamEnd));
                            } else {
                                //the player with the ball must appear in possession at frame_start and frame_end
                                CPIntVar playerStart = element(possession, frame_start);
                                CPIntVar playerEnd = element(possession, frame_end);
                                cp.post(neq(playerStart, -1));
                                cp.post(neq(playerStart, ball_idx));
                                cp.post(neq(playerEnd, -1));
                                cp.post(neq(playerEnd, ball_idx));
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
                                cp.post(le(extVars.get(extVars.size() - 3).var, frame_start));
                            }
                            counterEvent++;

                            int[][] player_pos = new int[teams.length][n]; // max size is the number of players
                            for (int i = 0; i < player_pos.length; i++) {
                                Arrays.fill(player_pos[i], -1);
                            }

                            CPIntVar player_id = makeIntVar(cp, teams.length);

                            if (player.id() != null) {
                                player_pos[0] = positionZones[player.id()];
                                cp.post(eq(player_id, player.id())); //should be useless with the element2D coming after

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
                                for (int i = 0; i < teams.length; i++) {
                                    player_pos[i] = positionZones[i];
                                }
                            }

                            //movement logic
                            if (payload instanceof int[]) {
                                int zone_start = ((int[]) payload)[0];
                                int zone_end = ((int[]) payload)[0];
                                cp.post(eq(element(player_pos, player_id, frame_end), zone_end));
                                cp.post(eq(element(player_pos, player_id, frame_start), zone_start));

                            } else if (payload instanceof Integer) {
                                int zone = (int) payload;
                                cp.post(eq(element(player_pos, player_id, frame_end), zone));
                                cp.post(neq(element(player_pos, player_id, frame_start), zone));
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
                            Team team = (Team) subject;
                            int team_int = team.name().equals("left") ? 0 : 1;
                            int[] zones = (int[]) payload;
                            cp.post(le(frame_start, frame_end));
                            if (counterEvent != 0) {
                                //frame_end of the event before < frame_start of this event
                                cp.post(le((extVars.get(extVars.size() - 3).var), frame_start));
                            }
                            counterEvent++;

                            //is in zone logic

                            List<Integer> playerIds = new ArrayList<>();
                            for (int i = 0; i < teams.length; i++) {
                                if (teams[i] == team_int) {
                                    playerIds.add(i);
                                }
                            }

                            for (int playerId : playerIds) {
                                int[] playerPos = positionZones[playerId];
                                CPBoolVar[] isInZone = new CPBoolVar[zones.length];
                                for (int z = 0; z < zones.length; z++) {
                                    isInZone[z] = isEq(element(playerPos, frame_start), zones[z]);
                                }
                                cp.post(or(isInZone));
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

                        //                    case "FORMATION" -> {
                        //                        Team team = (Team) subject;
                        //                        Formation formation = (Formation) payload;
                        //                        cp.post(le(frame_start, frame_end));
                        //                        if (counterEvent != 0) {
                        //                            //frame_end of the event before < frame_start of this event
                        //                            cp.post(le(extVars.get(extVars.size() - 3).var, frame_start));
                        //                        }
                        //                        counterEvent++;
                        //
                        //                        //TODO: formation logic (is it actually relevant?)
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
                for (ExtendedCPVar ev : finalExtVars) {
                    System.out.print("event #" + ev.event_idx + "(" + ev.order + ") = " + ev.type + " frame " + ev.var.max() + " " + "\n");
                }
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

        public ExtendedCPVar(CPIntVar var, int order, String type, int event_idx) {
            this.event_idx = event_idx;
            this.var = var;
            this.order = order;
            this.type = type;
        }
    }
}


