package org.main.sn.logic;

import org.main.util.ConstraintPattern;
import org.main.util.Instance;
import org.maxicp.cp.engine.core.CPSolver;
import org.main.util.BoundingBox;
import org.opencv.core.Mat;

import java.util.*;

public class Possession implements ConstraintPattern {

    int[] result;
    Map<Integer, List<Mat.Tuple2<Integer>>> possessionIntervalsByPlayer;

    public Possession(CPSolver cp, Instance instance) {
        apply(cp, instance);
    }

    @Override
    public void apply(CPSolver cp, Instance instance) {

        if (instance instanceof TrackingInstance soccer) {

            int n = soccer.n;
            Map<Integer, List<BoundingBox>> frames = soccer.frames;
            int[] players_right_idx = soccer.players_right_idx;
            int[] players_left_idx = soccer.players_left_idx;
            int ball_idx = soccer.ball_idx;

            int[] result = new int[n + 1];
            int threshold = 45;
            double[][] acc = soccer.acc;
            double[][] dthetas = soccer.dthetas;

            for (Map.Entry<Integer, List<BoundingBox>> entry : frames.entrySet()) {
                int frameID = entry.getKey();
                List<BoundingBox> frame = entry.getValue();
                double ball_x = -1;
                double ball_y = -1;
                double ball_h = -1;
                double ball_w = -1;
                for (BoundingBox box : frame) {
                    if (box.cls_id == ball_idx) {
                        ball_x = box.x;
                        ball_y = box.y;
                        ball_h = box.height;
                        ball_w = box.width;
                    }
                }
                if (ball_x == -1) {
                    result[frameID] = -1;
                    continue;
                }
                int closest_player = -1;
                double bestDist = Double.POSITIVE_INFINITY;
                for (BoundingBox box : frame) {
                    if ((Arrays.stream(players_right_idx).anyMatch(x -> x == box.cls_id)) || (Arrays.stream(players_left_idx).anyMatch(x -> x == box.cls_id))) {
                        //Euclidean distance
                        //double dx = box.x + box.width/2 - ball_x - ball_w/2;
                        //double dy = box.y + box.height/2 - ball_y - ball_h/2;
                        //double dist = Math.hypot(dx, dy);

                        //edge-to-edge distance
//                        double dx = Math.max(0, Math.max(box.x - (ball_x + ball_w),
//                                ball_x - (box.x + box.width)));
//                        double dy = Math.max(0, Math.max(box.y - (ball_y + ball_h),
//                                ball_y - (box.y + box.height)));
//                        double dist = Math.hypot(dx, dy);

                        //Euclidean distance with players feet
                        double dx = box.x + (double) box.width / 2 - ball_x - ball_w / 2;
                        double dy = box.y + box.height - ball_y - ball_h / 2;
                        double dist = Math.hypot(dx, dy);

                        if (dist < bestDist) {
                            bestDist = dist;
                            closest_player = box.cls_id;
                        }
                    }
                }
                double wAcc = 1.0;
                double wDir = 30.0;
                double displacement_ball_score = wAcc * acc[frameID][ball_idx] + wDir * dthetas[frameID][ball_idx];

                if (closest_player == -1) {
                    result[frameID] = -1;
                }
                if (displacement_ball_score > 47 && bestDist < threshold * 3) {
                    result[frameID] = closest_player;
                } else {
                    if (bestDist < threshold) {
                        result[frameID] = closest_player;
                    } else {
                        result[frameID] = -1;
                    }
                }
            }

            this.result = result;


        } else if (instance instanceof GameStateReconstructionInstance soccer) {
            // it's a GSRInstance

            Map<Integer, GameStateReconstructionInstance.FrameData> positions = soccer.positions;
            int n = soccer.n;
            int ball_idx = soccer.ball_idx;
            double[][] acc = soccer.acc;
            double[][] dthetas = soccer.dthetas;


            int[] result = new int[n + 1];
            result[0] = -1;
            double threshold = 0.058;

            for (Map.Entry<Integer, GameStateReconstructionInstance.FrameData> e : positions.entrySet()) {
                int frameID = e.getKey();
                GameStateReconstructionInstance.FrameData fd = e.getValue();

                if (fd == null || fd.players.isEmpty()) {
                    result[frameID] = -1;
                    continue;
                }

                GameStateReconstructionInstance.PlayerInfo ball = fd.players.get(ball_idx);
                if (ball == null) {
                    result[frameID] = -1;
                    continue;
                }

                // 1) Compute distance player-ball with 2D reconstruction

                double[] distancesReconstruction = new double[acc[0].length]; // distances[playersID] = distance to the ball for this player
                Arrays.fill(distancesReconstruction, Double.POSITIVE_INFINITY);
                for (Map.Entry<Integer, GameStateReconstructionInstance.PlayerInfo> pEntry : fd.players.entrySet()) {
                    GameStateReconstructionInstance.PlayerInfo pi = pEntry.getValue();
                    if (pi == null || pi.pos() == null) continue;

                    double dist_x = pi.pos().x() - ball.pos().x();
                    double dist_y = pi.pos().y() - ball.pos().y();
                    distancesReconstruction[pi.trackId()] = Math.hypot(dist_x, dist_y);
                }

                // 2) Compute distance player-ball with tracking information
                double[] distancesTracking = new double[acc[0].length]; // distances[playersID] = distance to the ball for this player

                for (Map.Entry<Integer, GameStateReconstructionInstance.PlayerInfo> pEntry : fd.players.entrySet()) {
                    GameStateReconstructionInstance.PlayerInfo pi = pEntry.getValue();
                    if (pi == null || pi.pos() == null) continue;

                    double dist_x = pi.pos().x_center() - ball.pos().x_center();
                    double dist_y = pi.pos().y_center() - pi.pos().h() / 2 - ball.pos().y_center();
                    distancesTracking[pi.trackId()] = Math.hypot(dist_x, dist_y);
                }

                // 3) Compute a score based on the trajectory of the ball
                double wAcc = 1.0;
                double wDir = 30.0;
                double displacement_ball_score = wAcc * acc[frameID][ball_idx] + wDir * dthetas[frameID][ball_idx];

                // 4) arbitrage between scores

                //TODO : don't take into account distancesReconstruction when the trajectory of the ball is super weird
                double bestDist = Double.POSITIVE_INFINITY;
                int closest_player = -1;
                double wtrack = 0.5 / 1000.0;
                double wrecons = 0.5 / 64.0;

                for (int pid = 1; pid < distancesTracking.length; pid++) {
                    if (pid == ball_idx) continue;

                    double dist = wtrack * distancesTracking[pid] + wrecons * distancesReconstruction[pid];
                    if (dist < bestDist) {
                        bestDist = dist;
                        closest_player = pid;
                    }
                }

                if (closest_player == -1) {
                    result[frameID] = -1;
                }
                if (displacement_ball_score > 47 && bestDist < threshold * 3) {
                    result[frameID] = closest_player;
                } else {
                    if (bestDist < threshold) {
                        result[frameID] = closest_player;
                    } else {
                        result[frameID] = -1;
                    }
                }

            }

            //if possession less than 3 frames, not really possession :
            int start = -1;
            for (int i = 1; i <= n; i++) {
                if (result[i] != -1) {
                    if (start == -1) start = i; // start of a possession
                } else {
                    if (start != -1) {
                        int length = i - start;
                        if (length <= 2) {
                            for (int j = start; j < i; j++) result[j] = -1;
                        }
                        start = -1;
                    }
                }
            }

            this.result = result;
            Set<Integer> playerIds = new HashSet<>();
            for (int pid = 0; pid < soccer.teams.length; pid++) {
                if (soccer.teams[pid] == 0 || soccer.teams[pid] == 1) {
                    playerIds.add(pid);
                }
            }
            this.possessionIntervalsByPlayer = possessionIntervalsByPlayer(
                    playerIds,
                    this.result
            );
        }



    }

    @Override
    public String getName() {
        return "Pass";
    }

    public static Map<Integer, List<Mat.Tuple2<Integer>>> possessionIntervalsByPlayer(Set<Integer> playerIds, int[] possession) {
        Map<Integer, List<Mat.Tuple2<Integer>>> intervalsByPlayer = new HashMap<>();
        for (int pid : playerIds) {
            intervalsByPlayer.put(pid, new ArrayList<>());
        }
        intervalsByPlayer.put(-1, new ArrayList<>());

        Integer currentPlayer = null; // tracked player currently holding the ball
        int currentStart = -1;

        for (int f = 0; f < possession.length; f++) {
            int holder = possession[f];
            //Integer trackedHolder = playerIds.contains(holder) ? holder : null;
            Integer trackedHolder = holder;

            if (currentPlayer == null) {
                // No open interval: start one if a tracked player holds now
                if (trackedHolder != null) {
                    currentPlayer = trackedHolder;
                    currentStart = f;
                }
            } else {
                // There is an open interval: close if holder changed (or left tracked set)
                if (trackedHolder == null || !trackedHolder.equals(currentPlayer)) {
                    intervalsByPlayer.get(currentPlayer).add(new Mat.Tuple2<>(currentStart, f - 1));
                    currentPlayer = null;
                    currentStart = -1;

                    // Start a new interval immediately if another tracked player holds now
                    if (trackedHolder != null) {
                        currentPlayer = trackedHolder;
                        currentStart = f;
                    }
                }
            }
        }

        // Close trailing interval if still open at the end
        if (currentPlayer != null) {
            intervalsByPlayer.get(currentPlayer).add(new Mat.Tuple2<>(currentStart, possession.length - 1));
        }

        return intervalsByPlayer;
    }

}