package org.main.sn.logic;

import org.main.util.ConstraintPattern;
import org.main.util.Instance;
import org.maxicp.cp.engine.core.CPSolver;

import java.util.*;

public class Possession implements ConstraintPattern {

    int[] result;
    boolean withTracking;

    public Possession(CPSolver cp, Instance instance, boolean withTracking) {
        this.withTracking = withTracking;
        apply(cp, instance);
    }

    @Override
    public void apply(CPSolver cp, Instance instance) {

        if (instance instanceof GameStateReconstructionInstance soccer) {
            // it's a GSRInstance

            Map<Integer, GameStateReconstructionInstance.FrameData> positions = soccer.positions;
            int n = soccer.n;
            int ball_idx = soccer.ball_idx;
            double[][] acc = soccer.acc;
            double[][] dthetas = soccer.dthetas;


            int[] result = new int[n + 1];
            result[0] = 0;
            double threshold = 0.058;

            for (Map.Entry<Integer, GameStateReconstructionInstance.FrameData> e : positions.entrySet()) {
                int frameID = e.getKey();
                GameStateReconstructionInstance.FrameData fd = e.getValue();

                if (fd == null || fd.players.isEmpty()) {
                    result[frameID] = 0;
                    continue;
                }

                GameStateReconstructionInstance.PlayerInfo ball = fd.players.get(ball_idx);
                if (ball == null) {
                    result[frameID] = 0;
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
                    distancesReconstruction[pi.id()] = Math.hypot(dist_x, dist_y);
                }

                // 2) Compute distance player-ball with tracking information
                double[] distancesTracking = new double[acc[0].length]; // distances[playersID] = distance to the ball for this player
                if (withTracking) {
                    for (Map.Entry<Integer, GameStateReconstructionInstance.PlayerInfo> pEntry : fd.players.entrySet()) {
                        GameStateReconstructionInstance.PlayerInfo pi = pEntry.getValue();
                        if (pi == null || pi.pos() == null) continue;

                        double dist_x = pi.pos().x_center() - ball.pos().x_center();
                        double dist_y = pi.pos().y_center() - pi.pos().h() / 2 - ball.pos().y_center();
                        distancesTracking[pi.id()] = Math.hypot(dist_x, dist_y);
                    }
                } else {
                    Arrays.fill(distancesTracking, 0);
                }

                // 3) Compute a score based on the trajectory of the ball
                double wAcc = 1.0;
                double wDir = 30.0;
                int activityThreshold = 47;
                double displacement_ball_score = wAcc * acc[frameID][ball_idx] + wDir * dthetas[frameID][ball_idx];

                // 4) arbitrage between scores

                //TODO : don't take into account distancesReconstruction when the trajectory of the ball is super weird
                double bestDist = Double.POSITIVE_INFINITY;
                double secondBestDist = Double.POSITIVE_INFINITY;
                int closest_player = -1;
                double wtrack = 0.5 / 1000.0;
                double wrecons = 0.5 / 64.0;

                for (int pid = 1; pid < distancesTracking.length; pid++) {
                    if (pid == ball_idx) continue;

                    double dist = wtrack * distancesTracking[pid] + wrecons * distancesReconstruction[pid];
                    if (dist < bestDist) {
                        secondBestDist = bestDist;
                        bestDist = dist;
                        closest_player = pid;
                    } else if (dist < secondBestDist) {
                        secondBestDist = dist;
                    }
                }

                if (closest_player == -1) {
                    result[frameID] = 0;
                }
                if (displacement_ball_score > activityThreshold && bestDist < threshold * 3) {
                    result[frameID] = closest_player;
                } else {

                    if (bestDist < threshold) {
                        result[frameID] = closest_player;
                    } else {
                        if (bestDist < threshold * 2 && secondBestDist > 4 * threshold) {
                            result[frameID] = closest_player;
                        } else {
                            result[frameID] = 0;
                        }
                    }
                }

            }

            this.result = result;
        }
    }

    @Override
    public String getName() {
        return "Pass";
    }

}