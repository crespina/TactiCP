package org.main;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.BoolVar;
import org.maxicp.search.*;
import org.maxicp.state.StateManager;
import org.maxicp.util.exception.InconsistencyException;
import org.util.BoundingBox;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;
import static org.util.And.and;
import static org.util.Overlap.overlapping;

public class Possession implements ConstraintPattern {

    public Possession() {
    }

    @Override
    public void apply(CPSolver cp, Instance instance) {

        long startTime = System.nanoTime();

        //Create instance
        SoccerInstance soccer = (SoccerInstance) instance;
        int n = soccer.n; // number of bboxes
        int[] cls_ids = soccer.cls_ids;
        int[] track_ids = soccer.track_ids;
        int num_tracks = Arrays.stream(track_ids).max().orElseThrow();
        int[] xs = soccer.xs;
        int[] ys = soccer.ys;
        int[] widths = soccer.widths;
        int[] heights = soccer.heights;
        int[] players_left = soccer.players_left_idx;
        int[] players_right = soccer.players_right_idx;
        int C = soccer.C;
        List<Integer> noBall = soccer.noBall;
        Map<Integer, BoundingBox> ball_pos = soccer.ball_pos;

        int pos_tol = 5; //tolerance on the position of the ball relative to the players
        Set<Integer> players_ids = IntStream.concat(Arrays.stream(players_left), Arrays.stream(players_right))
                .boxed()
                .collect(Collectors.toSet());

        List<List<Integer>> possession = new ArrayList<>();; //possession[f] contains the ID of the player possessing the ball at frame f
        for (int i = 0; i < num_tracks; i++) {
            possession.add(new ArrayList<>());
        }
        //Create variables

        for (int frame = 1; frame <= num_tracks; frame++) {
            try{
                if (noBall.contains(frame)) {
                    possession.get(frame-1).add(-1);
                    System.out.println(frame);
                    continue;
                }
                CPIntVar possessionF = makeIntVar(cp, n);

                cp.post(eq(element(track_ids, possessionF), frame));

                for (int i = 1; i <= C; i++) {
                    if (!players_ids.contains(i)) {
                        cp.post(neq(element(cls_ids, possessionF), i));
                    }
                }

                cp.post(overlapping(element(xs, possessionF), sum(element(xs, possessionF), element(widths, possessionF)), element(ys, possessionF), sum(element(ys, possessionF), element(heights, possessionF)),
                        ball_pos.get(frame).x, ball_pos.get(frame).x + ball_pos.get(frame).width, ball_pos.get(frame).y, ball_pos.get(frame).y + ball_pos.get(frame).height, pos_tol));


                DFSearch search = makeDfs(cp, firstFail(possessionF));
                final int finalFrame = frame;
                search.onSolution(() -> {
                            possession.get(finalFrame-1).add(element(cls_ids, possessionF).max());
                        }
                );
                search.solve();
            } catch (InconsistencyException e) {
                possession.get(frame-1).add(-1);
            }
        }
        for (int frame = 1; frame < num_tracks+1; frame++) {
            List<Integer> possiblePlayers = possession.get(frame-1);
            System.out.println("Frame " + frame + ": " + possiblePlayers);
        }

        long stopTime = System.nanoTime();
        System.out.println(stopTime - startTime);
    }

    @Override
    public String getName() {
        return "Pass";
    }

}