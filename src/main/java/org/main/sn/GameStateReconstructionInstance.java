package org.main.sn;

import org.main.util.Instance;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;

public class GameStateReconstructionInstance implements Instance {

    public Map<Integer, FrameData> positions;
    public TrajectoryMatrices trajectories;
    public int n;
    public int[] teams; //teams[playerID] = team of the player "left" or "right" (None if not applicable)

    public GameStateReconstructionInstance(String instanceFolderPath) throws IOException {
        File f = new File(instanceFolderPath + "/Labels-GameState.json");
        readPositions(f);
        n = positions.size();
        trajectories = computeTrajectories(positions);
    }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Root {
        public List<Annotation> annotations;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Annotation {
        public String id;
        @JsonProperty("image_id")
        public String imageId; //frame num
        @JsonProperty("track_id")
        public Integer trackId; //id to track the players
        @JsonProperty("category_id")
        public Integer categoryId; //1 = players, 2 = GK, 3 = referee, 4 = ball
        public Attributes attributes;
        @JsonProperty("bbox_pitch")
        public BBoxPitch bboxPitch;
        @JsonProperty("bbox_pitch_raw")
        public BBoxPitch bboxPitchRaw;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attributes {
        public String team; // "left" or "right", null for ball
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BBoxPitch {
        @JsonProperty("x_bottom_middle")
        public Double xBottomMiddle;
        @JsonProperty("y_bottom_middle")
        public Double yBottomMiddle;
    }

    public static class Position {
        public final double x;
        public final double y;

        public Position(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public String toString() {
            return String.format("(%.3f, %.3f)", x, y);
        }
    }

    public static class PlayerInfo {
        public final int trackId;
        public final String team;
        public final int categoryId;
        public final Position pos;

        public PlayerInfo(int trackId, String team, Position pos, int categoryId) {
            this.trackId = trackId;
            this.team = team;
            this.pos = pos;
            this.categoryId = categoryId;
        }

        @Override
        public String toString() {
            if (categoryId == 1) return String.format("Player team %s (%.3f, %.3f)", team, pos.x, pos.y);
            else if (categoryId == 2) return String.format("GK team %s (%.3f, %.3f)", team, pos.x, pos.y);
            return "null";
        }
    }

    public static class TrajectoryStep {

        public final double dx;
        public final double dy;

        public TrajectoryStep(double dx, double dy) {
            this.dx = dx;
            this.dy = dy;
        }

        @Override
        public String toString() {
            return String.format("(%.3f, %.3f)", dx, dy);
        }
    }


    public static class FrameData {
        public final Map<Integer, PlayerInfo> players = new HashMap<>(); // (player id, player info)
        public Position ball = null;
    }

    public static class FrameTrajectory {
        public final Map<Integer, TrajectoryStep> players = new HashMap<>();
        public TrajectoryStep ball = null;
    }


    /**
     * Return:
     * image_id -> FrameData(players + ball)
     */
    public void readPositions(File jsonFile) throws IOException {
        ObjectMapper om = new ObjectMapper();
        Root root = om.readValue(jsonFile, Root.class);
        Map<Integer, FrameData> frames = new TreeMap<>();

        int maxPlayerId = 0;

        Map<Integer, Integer> tmpTeams = new HashMap<>();  // pid -> teamInt

        for (Annotation ann : root.annotations) {
            if (ann.imageId == null) continue;

            int frameId;
            try {
                frameId = Integer.parseInt(ann.imageId) % 1000;
            } catch (NumberFormatException e) {
                continue; // skip weird frames
            }

            FrameData frame = frames.computeIfAbsent(frameId, k -> new FrameData());

            BBoxPitch b = ann.bboxPitch;
            if (b == null || b.xBottomMiddle == null || b.yBottomMiddle == null) continue;

            Position pos = new Position(b.xBottomMiddle, b.yBottomMiddle);

            if (ann.categoryId != null && (ann.categoryId == 1 || ann.categoryId == 2)) {
                if (ann.trackId != null) {
                    int pid = ann.trackId;
                    if (pid > maxPlayerId) maxPlayerId = pid;
                    String team = (ann.attributes != null) ? ann.attributes.team : "ball";
                    if (team != null && !tmpTeams.containsKey(pid)) {
                        if (team.equals("left")) tmpTeams.put(pid, 0);
                        else if (team.equals("right")) tmpTeams.put(pid, 1);
                        else if (team.equals("ball")) tmpTeams.put(pid, -1);
                    }
                    frame.players.put(
                            pid,
                            new PlayerInfo(pid, team, pos, ann.categoryId)
                    );
                }
            } else if (ann.categoryId != null && ann.categoryId == 4) {
                frame.ball = pos;
            }
        }

        int[] teams = new int[maxPlayerId + 2];  // +1 ball row, +1 because IDs start at 1
        Arrays.fill(teams, -1);

        for (var e : tmpTeams.entrySet()) {
            teams[e.getKey()] = e.getValue();
        }

        teams[maxPlayerId + 1] = -1;

        this.teams = teams;
        this.positions = frames;
    }

    public static class TrajectoryMatrices {
        public final double[][] dx;
        public final double[][] dy;
        public final int nPlayers;

        public TrajectoryMatrices(double[][] dx, double[][] dy, int nPlayers) {
            this.dx = dx;
            this.dy = dy;
            this.nPlayers = nPlayers;
        }
    }

    public static TrajectoryMatrices computeTrajectories(Map<Integer, FrameData> positions) {

        int nFrames = positions.size();
        int maxPlayerId = 0;

        // find max player ID
        for (FrameData fd : positions.values()) {
            for (int pid : fd.players.keySet()) {
                if (pid > maxPlayerId) maxPlayerId = pid;
            }
        }
        int nRows = maxPlayerId + 2; // last row = ball

        double[][] dx = new double[nRows][nFrames];
        double[][] dy = new double[nRows][nFrames];

        List<Integer> frameIds = new ArrayList<>(positions.keySet());
        Collections.sort(frameIds);

        Map<Integer, PlayerInfo> prevPlayers = null;
        Position prevBall = null;

        for (int f = 0; f < nFrames; f++) {
            int frameId = frameIds.get(f);
            FrameData curr = positions.get(frameId);

            // --- Players ---
            for (Map.Entry<Integer, PlayerInfo> entry : curr.players.entrySet()) {
                int pid = entry.getKey();
                PlayerInfo pInfo = entry.getValue();

                if (prevPlayers != null && prevPlayers.containsKey(pid)) {
                    Position prevPos = prevPlayers.get(pid).pos;
                    dx[pid][f] = pInfo.pos.x - prevPos.x;
                    dy[pid][f] = pInfo.pos.y - prevPos.y;
                } else {
                    dx[pid][f] = 0;
                    dy[pid][f] = 0;
                }
            }

            // --- Ball ---
            int ballRow = maxPlayerId + 1;
            if (curr.ball != null && prevBall != null) {
                dx[ballRow][f] = curr.ball.x - prevBall.x;
                dy[ballRow][f] = curr.ball.y - prevBall.y;
            } else {
                dx[ballRow][f] = 0;
                dy[ballRow][f] = 0;
            }

            prevPlayers = curr.players;
            prevBall = curr.ball;
        }

        return new TrajectoryMatrices(dx, dy, maxPlayerId + 1);
    }



    @Override
    public String toString() {
        return "TrackingInstance{";
    }


}
