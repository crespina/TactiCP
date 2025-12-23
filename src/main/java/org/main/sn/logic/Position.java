package org.main.sn.logic;

import org.main.util.BoundingBox;
import org.main.util.ConstraintPattern;
import org.main.util.Instance;
import org.maxicp.cp.engine.core.CPSolver;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class Position implements ConstraintPattern {

    int[] result;

    public Position(CPSolver cp, Instance instance) {
        apply(cp, instance);
    }

    @Override
    public void apply(CPSolver cp, Instance instance) {

        TrackingInstance soccer = (TrackingInstance) instance;
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
                    double dx = box.x + box.width / 2 - ball_x - ball_w / 2;
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

        //if possession less than 3 frames, not really possession :
//            int start = -1;
//            for (int i = 1; i <= n; i++) {
//                if (result[i] != -1) {
//                    if (start == -1) start = i; // start of a possession
//                } else {
//                    if (start != -1) {
//                        int length = i - start;
//                        if (length <= 2) {
//                            for (int j = start; j < i; j++) result[j] = -1;
//                        }
//                        start = -1;
//                    }
//                }
//            }

        this.result = result;
    }

    @Override
    public String getName() {
        return "Position";
    }
}
