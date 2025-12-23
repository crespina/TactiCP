package org.main.sn.logic;

import org.json.JSONArray;
import org.json.JSONObject;
import org.main.util.Instance;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;

import java.util.*;

public class PatternMatcher {

    private final JSONObject patternJson;
    private final Map<String, Integer> variableBindings;
    private final List<PatternEvent> events;
    public List<PatternMatch> matches;

    public PatternMatcher(String jsonPattern) {
        this.patternJson = new JSONObject(jsonPattern);
        this.variableBindings = new HashMap<>();
        this.events = new ArrayList<>();
        parsePattern();
    }

    private void parsePattern() {
        JSONObject pattern = patternJson.getJSONObject("pattern");
        JSONArray eventsArray = pattern.getJSONArray("events");

        for (int i = 0; i < eventsArray.length(); i++) {
            JSONObject eventJson = eventsArray.getJSONObject(i);
            String type = eventJson.getString("type");

            if ("pass".equals(type)) {
                events.add(new PassEvent(eventJson));
            } else if ("movement".equals(type)) {
                events.add(new MovementEvent(eventJson));
            }
        }
    }

    public void apply(CPSolver cp, Instance instance) {

    }

    // Inner classes for pattern representation
    private static abstract class PatternEvent {
        String id;
        String after;

        PatternEvent(JSONObject json) {
            this.id = json.getString("id");
            this.after = json.optString("after", null); //the sequence of events
        }
    }

    private static class PassEvent extends PatternEvent {
        String fromPlayerId;
        String toPlayerId;
        Integer fromZone;
        Integer toZone;
        String fromTeam;
        String toTeam;

        PassEvent(JSONObject json) {
            super(json);

            if (json.has("from")) {
                JSONObject from = json.getJSONObject("from");
                this.fromPlayerId = from.optString("playerId", null);
                this.fromZone = from.has("zone") ? from.getInt("zone") : null;
                this.fromTeam = from.optString("team", null);
            }

            if (json.has("to")) {
                JSONObject to = json.getJSONObject("to");
                this.toPlayerId = to.optString("playerId", null);
                this.toZone = to.has("zone") ? to.getInt("zone") : null;
                this.toTeam = to.optString("team", null);
            }
        }
    }

    private static class MovementEvent extends PatternEvent {
        int playerId;
        int fromZone;
        int toZone;

        MovementEvent(JSONObject json) {
            super(json);
            this.playerId = json.getInt("player");
            this.fromZone = json.getInt("fromZone");
            this.toZone = json.getInt("toZone");
        }
    }

    private static class EventVariables {
        CPIntVar startFrame;
        CPIntVar endFrame;
        CPIntVar fromPlayer;
        CPIntVar toPlayer;
    }

    public static class PatternMatch {
        private final Map<String, PassInfo> passes = new LinkedHashMap<>();

        void addEvent(String id, int fromPlayer, int toPlayer, int startFrame, int endFrame) {
            passes.put(id, new PassInfo(fromPlayer, toPlayer, startFrame, endFrame));
        }

        public Map<String, PassInfo> getPasses() {
            return passes;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("Pattern Match:\n");
            for (Map.Entry<String, PassInfo> entry : passes.entrySet()) {
                PassInfo info = entry.getValue();
                sb.append(String.format("  %s: Player %d -> Player %d (frames %d-%d)\n",
                        entry.getKey(), info.fromPlayer, info.toPlayer,
                        info.startFrame, info.endFrame));
            }
            return sb.toString();
        }
    }

    public record PassInfo(int fromPlayer, int toPlayer, int startFrame, int endFrame) {}

}
