package org.main.sn.logic;

import org.main.util.Instance;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.util.*;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


public class GameStateReconstructionInstance implements Instance {

    public Map<Integer, FrameData> positions;
    public int n;
    public int ball_idx = -1;
    public int maxId;
    public int[] GK_ids = new int[2]; // GK_ids[0] = left GK id, GK_ids[1] = right GK id
    public int[] teams; //teams[playerID] = team of the player "left" = 0 or "right"=1 (None if not applicable)
    public double[][] dx, dy, acc, dthetas;
    public String name;

    public GameStateReconstructionInstance(String instanceFolderPath) {
        this.name = instanceFolderPath.substring("data/SoccerNet/gamestate-2024/".length());
        File JsonFile = new File(instanceFolderPath + "/Labels-GameState.json");
        ObjectMapper om = new ObjectMapper();
        Root root = om.readValue(JsonFile, Root.class);
        Map<Integer, FrameData> frames = new TreeMap<>();

        int maxPlayerId = 0;

        Map<Integer, Integer> tmpTeams = new HashMap<>();  // pid -> teamInt

        for (Annotation ann : root.annotations) {
            if (ann.imageId == null) continue;

            int frameId;
            try {
                frameId = (int) (Long.parseLong(ann.imageId) % 1000 );
            } catch (NumberFormatException e) {
                continue; // skip weird frames
            }

            FrameData frame = frames.computeIfAbsent(frameId, k -> new FrameData());

            BBoxPitch bp = ann.bboxPitch;
            if (bp == null || bp.x == null || bp.y == null) continue;

            BBoxImage bi = ann.bboxImage;
            if (bi == null || bi.h == null || bi.w == null || bi.x_center == null || bi.y_center == null)
                continue;

            Position pos = new Position(bp.x, -bp.y, bi.x_center, bi.y_center, bi.w, bi.h); //we inverse the y axis here

            if (ann.categoryId != null && (ann.categoryId == 1 || ann.categoryId == 2 || ann.categoryId == 4)) {
                if (ann.trackId != null) {
                    int pid = ann.trackId;
                    if (ann.categoryId == 2) { // Goalkeepers
                        if (Objects.equals(ann.attributes.team, "left")){
                            GK_ids[0] = pid;
                        } else {
                            GK_ids[1] = pid;
                        }
                    }
                    if (pid > maxPlayerId) maxPlayerId = pid;
                    String team = (ann.attributes.team != null) ? ann.attributes.team : "ball";
                    if (!tmpTeams.containsKey(pid)) {
                        switch (team) {
                            case "left" -> tmpTeams.put(pid, 0);
                            case "right" -> tmpTeams.put(pid, 1);
                            case "ball" -> {
                                if (ball_idx != -1){
                                    tmpTeams.put(ball_idx, -1);
                                    pid = ball_idx;
                                } else {
                                    tmpTeams.put(pid, -1);
                                    ball_idx = pid;
                                }
                            }
                        }
                    }
                    frame.players.put(
                            pid,
                            new PlayerInfo(pid, team, pos)
                    );
                }
            }
        }

        int[] teams = new int[maxPlayerId + 1];
        Arrays.fill(teams, -1);

        for (var e : tmpTeams.entrySet()) {
            teams[e.getKey()] = e.getValue();
        }

        this.teams = teams;
        this.positions = frames;
        this.n = positions.size();

        int maxId = 0;

        // find max player ID
        for (FrameData fd : positions.values()) {
            for (int clsId : fd.players.keySet()) {
                if (clsId > maxId) maxId = clsId;
            }
        }

        this.maxId = maxId;

        double[][] dx = new double[n + 1][maxId + 1];
        double[][] dy = new double[n + 1][maxId + 1];
        double[][] ax = new double[n + 1][maxId + 1];
        double[][] ay = new double[n + 1][maxId + 1];
        double[][] acc = new double[n + 1][maxId + 1];
        double[][] angles = new double[n + 1][maxId + 1];
        double[][] dthetas = new double[n + 1][maxId + 1];

        for (int i = 0; i <= n; i++) {
            Arrays.fill(dx[i], Double.NaN);
            Arrays.fill(dy[i], Double.NaN);
            Arrays.fill(ax[i], Double.NaN);
            Arrays.fill(ay[i], Double.NaN);
        }

        List<Integer> frameIds = new ArrayList<>(positions.keySet());
        Collections.sort(frameIds);

        Map<Integer, PlayerInfo> prevPlayers = null;

        for (int frameID = 0; frameID < n; frameID++) {
            int frameId = frameIds.get(frameID);
            FrameData curr = positions.get(frameId);

            // --- Players ---
            for (Map.Entry<Integer, PlayerInfo> entry : curr.players.entrySet()) {
                int clsId = entry.getKey();
                PlayerInfo pInfo = entry.getValue();

                if (prevPlayers != null && prevPlayers.containsKey(clsId)) {
                    Position prevPos = prevPlayers.get(clsId).pos;
                    dx[frameID][clsId] = pInfo.pos.x - prevPos.x;
                    dy[frameID][clsId] = pInfo.pos.y - prevPos.y;
                    ax[frameID][clsId] = dx[frameID][clsId] - dx[frameID - 1][clsId];
                    ay[frameID][clsId] = dy[frameID][clsId] - dy[frameID - 1][clsId];
                    acc[frameID][clsId] = Math.sqrt(ax[frameID][clsId] * ax[frameID][clsId] + ay[frameID][clsId] * ay[frameID][clsId]);

                    angles[frameID][clsId] = Math.atan2(dy[frameID][clsId], dx[frameID][clsId]);
                    double dtheta = Math.abs(angles[frameID][clsId] - angles[frameID - 1][clsId]);
                    if (dtheta > Math.PI) {
                        dtheta = 2 * Math.PI - dtheta;
                    }
                    dthetas[frameID][clsId] = dtheta;
                } else {
                    dx[frameID][clsId] = 0;
                    dy[frameID][clsId] = 0;
                }
            }
            prevPlayers = curr.players;
        }

        this.dx = dx;
        this.dy = dy;
        this.acc = acc;
        this.dthetas = dthetas;

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
        @JsonProperty("bbox_image")
        public BBoxImage bboxImage;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Attributes {
        public String team; // "left" or "right", or "ball"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BBoxPitch {
        @JsonProperty("x_bottom_middle")
        public Double x;
        @JsonProperty("y_bottom_middle")
        public Double y;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BBoxImage {
        @JsonProperty("x_center")
        public Double x_center;
        @JsonProperty("y_center")
        public Double y_center;
        @JsonProperty("w")
        public Integer w;
        @JsonProperty("h")
        public Integer h;
    }

    public record Position(double x, double y, double x_center, double y_center, double w, double h) {
    }

    public record PlayerInfo(int trackId, String team, Position pos) {
    }

    public static class FrameData {
        public final Map<Integer, PlayerInfo> players = new HashMap<>(); // (player id, player info)
    }


    @Override
    public String toString() {
        return "GameStateReconstructionInstance{" +
                "n=" + n +
                ", ball_idx=" + ball_idx +
                ", maxId=" + maxId +
                ", teams=" + Arrays.toString(teams) +
                '}';
    }

}