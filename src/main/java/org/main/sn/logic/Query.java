package org.main.sn.logic;

import org.main.sn.dsl.*;
import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.cp.engine.core.CPVar;

import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.maxicp.cp.CPFactory.*;

public class Query {

    public void apply(Sequence seq){

        CPSolver cp = CPFactory.makeSolver();
        Hashtable<String, CPVar> vars = new Hashtable<>();

        final List<Event> steps = seq.steps;
        final List<GameStateReconstructionInstance> matches = seq.matches;
        int total_duration = seq.duration;
        int total_start = seq.start;
        int total_end = seq.end;

        for (GameStateReconstructionInstance instance : matches) {

            int[] teams = instance.teams;
            int n = instance.n;
            Possession p = new Possession(cp, instance);
            int[] possession = p.result;

            int counter = 0;

            for (Event event : steps) {
                Action action = event.action;
                Entity subject = event.subject;
                int event_timeStart = event.timeStart;
                int event_timeEnd = event.timeEnd;
                int event_duration = event.duration;
                Object payload = action.payload;

                CPIntVar frame_start = CPFactory.makeIntVar(cp, n);
                CPIntVar frame_end = CPFactory.makeIntVar(cp, n);

                if (event_timeStart != -1) {
                    cp.post(ge(frame_start, event_timeStart));
                }
                if (event_timeEnd != -1) {
                    cp.post(le(frame_end, event_timeEnd));
                }
                if (event_duration != -1) {
                    cp.post(le(sum(frame_end,minus(frame_start)), event_duration));
                }

                switch (action.name) {

                    //ball event
                    case "BALL_MOVE_TO" -> {
                        int zone = (int) payload;
                        vars.put("frame_event_" + counter + "_start", frame_start);
                        vars.put("frame_event_" + counter + "_end", frame_end);
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            cp.post(le((CPIntVar) vars.get("frame_event_" + (counter-1) + "_start"), frame_start));
                        }
                        counter++;
                    }

                    //player event
                    case "PASS_TO" -> {
                        Player player_from = (Player) subject;
                        Player player_to = (Player) payload;
                        vars.put("frame_event_" + counter + "_start", frame_start);
                        vars.put("frame_event_" + counter + "_end", frame_end);
                        vars.put(player_from.name(), element(possession, frame_start));
                        vars.put(player_to.name(), element(possession, frame_end));
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            cp.post(le((CPIntVar) vars.get("frame_event_" + (counter-1) + "_start"), frame_start));
                        }
                        counter++;

                        //pass logic

                    }
                    case "HAS_BALL" -> {
                        Player player = (Player) subject;
                        vars.put("frame_event_" + counter + "_start", frame_start);
                        vars.put("frame_event_" + counter + "_end", frame_end);
                        vars.put(player.name(), element(possession, frame_start));
                        vars.put(player.name(), element(possession, frame_end));
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            cp.post(le((CPIntVar) vars.get("frame_event_" + (counter-1) + "_start"), frame_start));
                        }
                        counter++;

                        //possession logic
                    }
                    case "MOVE_TO" -> {
                        Player player = (Player) subject;
                        int zone = (int) payload;
                        vars.put("frame_event_" + counter + "_start", frame_start);
                        vars.put("frame_event_" + counter + "_end", frame_end);
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            cp.post(le((CPIntVar) vars.get("frame_event_" + (counter-1) + "_start"), frame_start));
                        }
                        counter++;

                        //movement logic
                    }

                    //team event
                    case "IS_IN_ZONE" -> {
                        Team team = (Team) subject;
                        int zone = (int) payload;
                        vars.put("frame_event_" + counter + "_start", frame_start);
                        vars.put("frame_event_" + counter + "_end", frame_end);
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            cp.post(le((CPIntVar) vars.get("frame_event_" + (counter-1) + "_start"), frame_start));
                        }
                        counter++;

                        //is in zone logic

                    }

                    case "FORMATION" -> {
                        Team team = (Team) subject;
                        Formation formation = (Formation) payload;
                        vars.put("frame_event_" + counter + "_start", frame_start);
                        vars.put("frame_event_" + counter + "_end", frame_end);
                        cp.post(le(frame_start, frame_end));
                        if (counter != 0) {
                            cp.post(le((CPIntVar) vars.get("frame_event_" + (counter-1) + "_start"), frame_start));
                        }
                        counter++;

                        //formation logic
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
            if (total_duration != -1){
                cp.post(le(sum(lastVar,minus(firstVar)), total_duration));
            }
            if (total_start != -1){
                cp.post(ge(firstVar, total_start));
            }
            if (total_end != -1) {
                cp.post(le(lastVar, total_end));
            }
        }

    }
}
