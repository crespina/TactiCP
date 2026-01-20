package org.main.sn.logic;

import org.main.util.ConstraintPattern;
import org.main.util.Instance;
import org.maxicp.cp.engine.core.CPSolver;

import java.util.Arrays;
import java.util.Map;

public class Position implements ConstraintPattern {

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
                Arrays.fill(position[i], -1);
            }

            for (int frame : positions.keySet()) {
                GameStateReconstructionInstance.FrameData frameData = positions.get(frame);
                Map<Integer, GameStateReconstructionInstance.PlayerInfo> players = frameData.players;

                players.forEach((pid, player) -> {

                    GameStateReconstructionInstance.Position bboxPitch = player.pos();

                    int zone = bboxPitch.x() < -40 ? 0 :
                            bboxPitch.x() < 0 ? 1 :
                                    bboxPitch.x() < 40 ? 2 : 3;

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
