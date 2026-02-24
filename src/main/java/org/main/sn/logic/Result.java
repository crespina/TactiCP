package org.main.sn.logic;

import org.maxicp.cp.engine.core.CPIntervalVar;

import java.util.ArrayList;
import java.util.List;

public class Result {

        public final int solutionIndex;
        public final List<ResultEvent> events; // top-level sequence (includes GroupEvent nodes)

        public Result(int solutionIndex, List<ResultEvent> events) {
            this.solutionIndex = solutionIndex;
            this.events = List.copyOf(events);
        }

        // convenience: produce the exact same List<String> output you had (for backward compat / debug)
        public List<String> toFormattedStrings() {
            List<String> out = new ArrayList<>();
            out.add("solution #" + solutionIndex);
            out.add("------------------\n");
            for (ResultEvent e : events) {
                appendEventStrings(out, e, 0);
                out.add("\n");
            }
            return out;
        }

        private void appendEventStrings(List<String> out, ResultEvent e, int indent) {
            String pad = " ".repeat(indent);
            if (e instanceof GroupEvent ge) {
                out.add(pad + " THE NEXT " + ge.subSize + " EVENTS ARE PART OF THE \"" + ge.groupKind + "\" | frames: " + ge.interval);
                for (ResultEvent child : ge.children) appendEventStrings(out, child, indent + 2);
            } else if (e instanceof PassEvent p) {
                out.add(pad + "EVENT # " + p.eventIdx + (p.negated ? " NOT PASS" : " PASS"));
                out.add(pad + " From player ID " + p.passerId + " to player ID " + p.receiverId + " | frames: " + p.interval);
            } else if (e instanceof PossessionEvent ps) {
                out.add(pad + "EVENT # " + ps.eventIdx + (ps.negated ? " DOES NOT HAVE BALL" : " HAS BALL"));
                out.add(pad + " Player ID " + ps.playerIds + " | frames: " + ps.interval);
            } else if (e instanceof PlayerMoveEvent mv) {
                out.add(pad + "EVENT # " + mv.eventIdx + (mv.negated ? " DOES NOT MOVE TO" : " MOVES TO"));
                out.add(pad + " Player ID " + mv.playerId + " | frames: " + mv.interval);
            } else if (e instanceof TeamMoveEvent tm) {
                out.add(pad + "EVENT # " + tm.eventIdx + (tm.negated ? " DOES NOT MOVE TO" : " MOVES TO"));
                out.add(pad + " Players in the team " + tm.playerIds + " | frames: " + tm.interval);
            } else if (e instanceof PositionEvent psn) {
                out.add(pad + "EVENT # " + psn.eventIdx + (psn.negated ? " IS NOT IN ZONE" : " IS IN ZONE"));
                out.add(pad + (psn.playerIds.size() > 1 ? "TEAM " : "PLAYER ") + psn.playerIds + " | frames: " + psn.interval);
            }
        }

    @Override
    public String toString() {
        return super.toString();
    }

    // base event
    public static abstract class ResultEvent {
        public final int eventIdx;
        public final String type;
        public final boolean negated;
        public final CPIntervalVar interval;
        protected ResultEvent(int eventIdx, String type, boolean negated, CPIntervalVar interval) {
            this.eventIdx = eventIdx; this.type = type; this.negated = negated; this.interval = interval;
        }
    }

    // concrete event types
    public static final class PassEvent extends ResultEvent {
        public final int passerId;
        public final int receiverId;
        public PassEvent(int idx, boolean neg, CPIntervalVar interval, int passer, int receiver) {
            super(idx, "PASS_TO", neg, interval);
            this.passerId = passer; this.receiverId = receiver;
        }
    }
    public static final class PossessionEvent extends ResultEvent {
        public final List<Integer> playerIds;
        public PossessionEvent(int idx, boolean neg, CPIntervalVar interval, List<Integer> players) {
            super(idx, "POSSESSION", neg, interval);
            this.playerIds = List.copyOf(players);
        }
    }
    public static final class PlayerMoveEvent extends ResultEvent {
        public final int playerId;
        public PlayerMoveEvent(int idx, boolean neg, CPIntervalVar interval, int playerId) {
            super(idx, "PLAYER_MOVE_TO", neg, interval);
            this.playerId = playerId;
        }
    }
    public static final class TeamMoveEvent extends ResultEvent {
        public final List<Integer> playerIds;
        public TeamMoveEvent(int idx, boolean neg, CPIntervalVar interval, List<Integer> players) {
            super(idx, "TEAM_MOVE_TO", neg, interval);
            this.playerIds = List.copyOf(players);
        }
    }
    public static final class PositionEvent extends ResultEvent {
        public final List<Integer> playerIds;
        public PositionEvent(int idx, boolean neg, CPIntervalVar interval, List<Integer> players) {
            super(idx, "POSITION", neg, interval);
            this.playerIds = List.copyOf(players);
        }
    }

    // grouping (AND/OR interval) - contains its children
    public static final class GroupEvent extends ResultEvent {
        public final String groupKind; // "AND" or "OR"
        public final int subSize;      // how many child events belong
        public final List<ResultEvent> children = new ArrayList<>();
        public GroupEvent(int idx, String groupKind, int subSize, CPIntervalVar interval) {
            super(idx, groupKind + "_interval", false, interval);
            this.groupKind = groupKind; this.subSize = subSize;
        }
    }
}
