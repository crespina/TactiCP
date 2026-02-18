package org.main.sn.logic;

import org.main.util.ConstraintPattern;
import org.main.util.Instance;
import org.maxicp.cp.engine.core.CPSolver;

import java.util.Arrays;
import java.util.Map;

public class Position implements ConstraintPattern {

    //pitch_width = 105 + 2 * 10
    //pitch_height = 68 + 2 * 5

    int[][] position; // [player][frame] -> zone

    public Position(CPSolver cp, Instance instance) {
        apply(cp, instance);
    }

    @Override
    public void apply(CPSolver cp, Instance instance) {
        if (instance instanceof TrackingInstance soccer) {
            //TODO: implement position logic for TrackingInstance

        } else if (instance instanceof GameStateReconstructionInstance soccer) {

            int n = soccer.n;
            int maxId = soccer.maxId;
            Map<Integer, GameStateReconstructionInstance.FrameData> positions = soccer.positions;

            position = new int[maxId + 1][n + 1];
            for (int i = 0; i <= maxId; i++) {
                Arrays.fill(position[i], 0);
            }

            for (int frame : positions.keySet()) {
                GameStateReconstructionInstance.FrameData frameData = positions.get(frame);
                Map<Integer, GameStateReconstructionInstance.PlayerInfo> players = frameData.players;

                players.forEach((pid, player) -> {

                    GameStateReconstructionInstance.Position bboxPitch = player.pos();

                    int zone;
                    double x = bboxPitch.x();
                    double y = bboxPitch.y();

                    if (x >= -31.25 && x <= 0 && y >= 0 && y <= 20) zone = 1;
                    else if (x >= 0 && x <= 31.25 && y >= 0 && y <= 20) zone = 2;
                    else if (x >= 0 && x <= 31.25 && y >= -20 && y <= 0) zone = 3;
                    else if (x >= -31.25 && x <= 0 && y >= -20 && y <= 0) zone = 4;
                    else if (x >= -31.25 && x <= 0 && y > 20) zone = 6;
                    else if (x >= 0 && x <= 31.25 && y > 20) zone = 7;
                    else if (x >= 0 && x <= 31.25 && y < -20) zone = 10;
                    else if (x >= -31.25 && x <= 0 && y < -20) zone = 11;
                    else if (x <= -46 && y >= -20 && y <= 20) zone = 13;
                    else if (x >= 46 && y >= -20 && y <= 20) zone = 14;
                    else {
                        if (y < 0) {
                            if (x < 0) {
                                zone = 12;
                            } else {
                                zone = 9;
                            }
                        } else {
                            if (x < 0) {
                                zone = 5;
                            } else {
                                zone = 8;
                            }
                        }
                    }

                    position[pid][frame] = zone;
                });
            }
        }
    }

    @Override
    public String getName() {
        return "Position";
    }
}
