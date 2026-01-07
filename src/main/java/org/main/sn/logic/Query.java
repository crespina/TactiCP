package org.main.sn.logic;

import org.main.sn.dsl.*;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.CPVar;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.maxicp.cp.CPFactory.*;

public class Query {

    public void apply(Sequence seq) {

        CPSolver cp = CPFactory.makeSolver();
        LinkedHashMap<String, CPVar> vars = new LinkedHashMap<>();

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

            int[] teams = instance.teams;
            int n = instance.n;
            int ball_idx = instance.ball_idx;
            Possession p = new Possession(cp, instance);
            int[] possession = p.result;
            Position pos = new Position(cp, instance);
            int[][] position = pos.position;

            int counter = 0;

            for (Event event : steps) {
                Action action = event.action;
                Entity subject = event.subject;
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
                        vars.put("frame_event_ball_move_to_" + counter + "_start", frame_start);
                        vars.put("frame_event_ball_move_to_" + counter + "_end", frame_end);
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            //frame_end of the event before < frame_start of this event
                            cp.post(le((CPIntVar) vars.get(vars.size() - 3), frame_start));
                        }
                        counter++;

                        //ball movement logic

                        if (payload instanceof int[]) {
                            int zone_start = ((int[]) payload)[0];
                            int zone_end = ((int[]) payload)[0];
                            int[] ball_pos = position[ball_idx];
                            cp.post(eq(element(ball_pos, frame_end), zone_end));
                            cp.post(eq(element(ball_pos, frame_start), zone_start));

                        } else if (payload instanceof Integer) {
                            int zone = (int) payload;
                            int[] ball_pos = position[ball_idx];
                            cp.post(eq(element(ball_pos, frame_end), zone));
                            cp.post(neq(element(ball_pos, frame_start), zone));
                        }

                        // circle / rectangle constraints
                        if (event_circle) {

                        }

                        if (total_circle) {

                        }

                        if (event_rectangle) {

                        }

                        if (total_rectangle) {

                        }
                    }

                    //PLAYER EVENTS

                    case "PASS_TO" -> {
                        Player player_from = (Player) subject;
                        Player player_to = (Player) payload;
                        vars.put("frame_event_pass_to_" + counter + "_start", frame_start);
                        vars.put("frame_event_pass_to_" + counter + "_end", frame_end);
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            //frame_end of the event before < frame_start of this event
                            cp.post(le((CPIntVar) vars.get(vars.size() - 3), frame_start));
                        }
                        counter++;

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

                        //left = 0, right = 1
                        int[] passersTeam = Arrays.stream(passersIds).map(id -> teams[id]).toArray();
                        int[] receiversTeam = Arrays.stream(receiversIds).map(id -> teams[id]).toArray();

                        CPIntVar pass_index = makeIntVar(cp, res.size());
                        CPIntVar passer_id = element(passersIds, pass_index);
                        CPIntVar receiver_id = element(receiversIds, pass_index);

                        String caseKey = (player_from.id() != null ? "ID" : player_from.team() != null ? "TEAM" : "NAME") +
                                "_" + (player_to.id() != null ? "ID" : player_to.team() != null ? "TEAM" : "NAME");

                        switch (caseKey) {
                            case "ID_ID" -> { /* both by ID */
                                cp.post(eq(passer_id, player_from.id()));
                                cp.post(eq(receiver_id, player_to.id()));
                            }
                            case "ID_TEAM" -> { /* from ID, to team */
                                int team = player_to.team().equals("left") ? 0 : 1;
                                cp.post(eq(passer_id, player_from.id()));
                                cp.post(eq(element(receiversTeam, pass_index), team));
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

                        // circle / rectangle constraints
                        if (event_circle) {

                        }

                        if (total_circle) {

                        }

                        if (event_rectangle) {

                        }

                        if (total_rectangle) {

                        }
                    }
                    case "HAS_BALL" -> {
                        Player player = (Player) subject;
                        vars.put("frame_event_has_ball_" + counter + "_start", frame_start);
                        vars.put("frame_event_has_ball_" + counter + "_end", frame_end);
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            //frame_end of the event before < frame_start of this event
                            cp.post(le((CPIntVar) vars.get(vars.size() - 3), frame_start));
                        }
                        counter++;

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
                            CPIntVar playerStart = element(possession,frame_start);
                            CPIntVar playerEnd = element(possession,frame_end);
                            cp.post(neq(playerStart,-1));
                            cp.post(neq(playerStart,ball_idx));
                            cp.post(neq(playerEnd,-1));
                            cp.post(neq(playerEnd,ball_idx));
                        }

                        // circle / rectangle constraints
                        if (event_circle) {

                        }

                        if (total_circle) {

                        }

                        if (event_rectangle) {

                        }

                        if (total_rectangle) {

                        }
                    }
                    case "MOVE_TO" -> {
                        Player player = (Player) subject;
                        vars.put("frame_event_move_to_" + counter + "_start", frame_start);
                        vars.put("frame_event_move_to_" + counter + "_end", frame_end);
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            //frame_end of the event before < frame_start of this event
                            cp.post(le((CPIntVar) vars.get(vars.size() - 3), frame_start));
                        }
                        counter++;

                        int[][] player_pos = new int[teams.length][n]; // max size is the number of players
                        for (int i = 0; i < player_pos.length; i++) {
                            Arrays.fill(player_pos[i], -1);
                        }

                        CPIntVar player_id = makeIntVar(cp, teams.length);

                        if (player.id() != null) {
                            player_pos[0] = position[player.id()];
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
                                player_pos[i] = position[playerIds.get(i)];
                            }
                        } else {
                            //all players
                            for (int i = 0; i < teams.length; i++) {
                                player_pos[i] = position[i];
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

                        // circle / rectangle constraints
                        if (event_circle) {

                        }

                        if (total_circle) {

                        }

                        if (event_rectangle) {

                        }

                        if (total_rectangle) {

                        }
                    }

                    //TEAM EVENTS

                    case "IS_IN_ZONES" -> {
                        Team team = (Team) subject;
                        int team_int = team.name().equals("left") ? 0 : 1;
                        int[] zones = (int[]) payload;
                        vars.put("frame_event_is_in_zone_" + counter + "_start", frame_start);
                        vars.put("frame_event_is_in_zone_" + counter + "_end", frame_end);
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            //frame_end of the event before < frame_start of this event
                            cp.post(le((CPIntVar) vars.get(vars.size() - 3), frame_start));
                        }
                        counter++;

                        //is in zone logic

                        List<Integer> playerIds = new ArrayList<>();
                        for (int i = 0; i < teams.length; i++) {
                            if (teams[i] == team_int) {
                                playerIds.add(i);
                            }
                        }

                        for (int playerId : playerIds){
                            int[] playerPos = position[playerId];
                            CPBoolVar[] isInZone = new CPBoolVar[zones.length];
                            for (int z = 0; z<zones.length; z++){
                                isInZone[z] = isEq(element(playerPos, frame_start), zones[z]);
                            }
                            cp.post(or(isInZone));
                        }

                        // circle / rectangle constraints
                        if (event_circle) {

                        }

                        if (total_circle) {

                        }

                        if (event_rectangle) {

                        }

                        if (total_rectangle) {

                        }
                    }

                    case "FORMATION" -> {
                        Team team = (Team) subject;
                        Formation formation = (Formation) payload;
                        vars.put("frame_event_formation_" + counter + "_start", frame_start);
                        vars.put("frame_event_formation_" + counter + "_end", frame_end);
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            //frame_end of the event before < frame_start of this event
                            cp.post(le((CPIntVar) vars.get(vars.size() - 3), frame_start));
                        }
                        counter++;

                        //TODO: formation logic (is it actually relevant?)
                    }
                }
            }

            //total duration
            Pattern frame_event_pattern = Pattern.compile("frame_event_(\\d+)_end");
            CPIntVar lastVar = (CPIntVar)
                    vars.entrySet().stream()
                            .filter(e -> frame_event_pattern.matcher(e.getKey()).matches())
                            .max(Comparator.comparingInt(e -> {
                                Matcher m = frame_event_pattern.matcher(e.getKey());
                                m.matches();
                                return Integer.parseInt(m.group(1));
                            }))
                            .map(Map.Entry::getValue)
                            .orElse(null);

            CPIntVar firstVar = (CPIntVar) vars.get("frame_event_0_start");
            if (total_duration != -1) {
                cp.post(le(sum(lastVar, minus(firstVar)), total_duration));
            }
            if (total_start != -1) {
                cp.post(ge(firstVar, total_start));
            }
            if (total_end != -1) {
                cp.post(le(lastVar, total_end));
            }

            //total zone

            //TODO: all actions in radius / rectangle
        }
    }
}
