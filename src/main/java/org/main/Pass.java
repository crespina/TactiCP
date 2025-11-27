package org.main;

import org.maxicp.cp.CPFactory;
import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.modeling.BoolVar;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;
import org.maxicp.search.StopSearchException;
import org.maxicp.state.StateManager;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.maxicp.cp.CPFactory.*;
import static org.maxicp.search.Searches.*;
import static org.util.And.and;
import static org.util.Overlap.overlapping;

public class Pass implements ConstraintPattern {

    public Pass() {
    }

    @Override
    public void apply(CPSolver cp, Instance instance) {
        //Create instance
        SoccerInstance soccer = (SoccerInstance) instance;
        int n = soccer.n; // number of bboxes
        int[] cls_ids = soccer.cls_ids;
        int[] track_ids = soccer.track_ids;
        int num_tracks = Arrays.stream(track_ids).max().orElseThrow();
        ;
        int[] xs = soccer.xs;
        int[] ys = soccer.ys;
        int[] widths = soccer.widths;
        int[] heights = soccer.heights;
        int[] players_left = soccer.players_left_idx;
        int[] players_right = soccer.players_right_idx;
        int ball = soccer.ball_idx;
        int[] ref = soccer.referees_idx;
        int C = soccer.C;
        List<Integer> noBall = soccer.noBall;

        int pos_tol = 5; //tolerance on the position of the ball relative to the players

        //Create variables : pattern to be matched
        CPIntVar[] pattern = makeIntVarArray(cp, 4, n); // pattern[0] represents the passer and pattern[1] represents the receiver, pattern[2] represents the ball at the start; pattern[3] represents the ball at the end

        CPIntVar passer_x = element(xs, pattern[0]);
        CPIntVar passer_y = element(ys, pattern[0]);
        CPIntVar passer_w = element(widths, pattern[0]);
        CPIntVar passer_h = element(heights, pattern[0]);
        CPIntVar passer_cls_id = element(cls_ids, pattern[0]);
        CPIntVar passer_track = element(track_ids, pattern[0]);

        CPIntVar receiver_x = element(xs, pattern[1]);
        CPIntVar receiver_y = element(ys, pattern[1]);
        CPIntVar receiver_w = element(widths, pattern[1]);
        CPIntVar receiver_h = element(heights, pattern[1]);
        CPIntVar receiver_cls_id = element(cls_ids, pattern[1]);
        CPIntVar receiver_track = element(track_ids, pattern[1]);

        CPIntVar ball_passer_x = element(xs, pattern[2]);
        CPIntVar ball_passer_y = element(ys, pattern[2]);
        CPIntVar ball_passer_w = element(widths, pattern[2]);
        CPIntVar ball_passer_h = element(heights, pattern[2]);
        CPIntVar ball_passer_cls_id = element(cls_ids, pattern[2]);
        CPIntVar ball_passer_track = element(track_ids, pattern[2]);

        CPIntVar ball_receiver_x = element(xs, pattern[3]);
        CPIntVar ball_receiver_y = element(ys, pattern[3]);
        CPIntVar ball_receiver_w = element(widths, pattern[3]);
        CPIntVar ball_receiver_h = element(heights, pattern[3]);
        CPIntVar ball_receiver_cls_id = element(cls_ids, pattern[3]);
        CPIntVar ball_receiver_track = element(track_ids, pattern[3]);

        CPIntVar pred_pass = makeIntVar(cp, n);
        CPIntVar succ_pass = makeIntVar(cp, n);
        CPIntVar pred_rec = makeIntVar(cp, n);
        CPIntVar succ_rec = makeIntVar(cp, n);
        CPIntVar pred_ball_pass = makeIntVar(cp, n);
        CPIntVar succ_ball_pass = makeIntVar(cp, n);
        CPIntVar pred_ball_rec = makeIntVar(cp, n);
        CPIntVar succ_ball_rec = makeIntVar(cp, n);

        CPIntVar pred_passer_x = element(xs, pred_pass);
        CPIntVar pred_passer_y = element(ys, pred_pass);
        CPIntVar pred_passer_w = element(widths, pred_pass);
        CPIntVar pred_passer_h = element(heights, pred_pass);
        CPIntVar pred_passer_cls_id = element(cls_ids, pred_pass);
        CPIntVar pred_passer_track = element(track_ids, pred_pass);

        CPIntVar succ_passer_x = element(xs, succ_pass);
        CPIntVar succ_passer_y = element(ys, succ_pass);
        CPIntVar succ_passer_w = element(widths, succ_pass);
        CPIntVar succ_passer_h = element(heights, succ_pass);
        CPIntVar succ_passer_cls_id = element(cls_ids, succ_pass);
        CPIntVar succ_passer_track = element(track_ids, succ_pass);

        CPIntVar pred_receiver_x = element(xs, pred_rec);
        CPIntVar pred_receiver_y = element(ys, pred_rec);
        CPIntVar pred_receiver_w = element(widths, pred_rec);
        CPIntVar pred_receiver_h = element(heights, pred_rec);
        CPIntVar pred_receiver_cls_id = element(cls_ids, pred_rec);
        CPIntVar pred_receiver_track = element(track_ids, pred_rec);

        CPIntVar succ_receiver_x = element(xs, succ_rec);
        CPIntVar succ_receiver_y = element(ys, succ_rec);
        CPIntVar succ_receiver_w = element(widths, succ_rec);
        CPIntVar succ_receiver_h = element(heights, succ_rec);
        CPIntVar succ_receiver_cls_id = element(cls_ids, succ_rec);
        CPIntVar succ_receiver_track = element(track_ids, succ_rec);

        CPIntVar pred_ball_passer_x = element(xs, pred_ball_pass);
        CPIntVar pred_ball_passer_y = element(ys, pred_ball_pass);
        CPIntVar pred_ball_passer_w = element(widths, pred_ball_pass);
        CPIntVar pred_ball_passer_h = element(heights, pred_ball_pass);
        CPIntVar pred_ball_passer_cls_id = element(cls_ids, pred_ball_pass);
        CPIntVar pred_ball_passer_track = element(track_ids, pred_ball_pass);

        CPIntVar succ_ball_passer_x = element(xs, succ_ball_pass);
        CPIntVar succ_ball_passer_y = element(ys, succ_ball_pass);
        CPIntVar succ_ball_passer_w = element(widths, succ_ball_pass);
        CPIntVar succ_ball_passer_h = element(heights, succ_ball_pass);
        CPIntVar succ_ball_passer_cls_id = element(cls_ids, succ_ball_pass);
        CPIntVar succ_ball_passer_track = element(track_ids, succ_ball_pass);

        CPIntVar pred_ball_receiver_x = element(xs, pred_ball_rec);
        CPIntVar pred_ball_receiver_y = element(ys, pred_ball_rec);
        CPIntVar pred_ball_receiver_w = element(widths, pred_ball_rec);
        CPIntVar pred_ball_receiver_h = element(heights, pred_ball_rec);
        CPIntVar pred_ball_receiver_cls_id = element(cls_ids, pred_ball_rec);
        CPIntVar pred_ball_receiver_track = element(track_ids, pred_ball_rec);

        CPIntVar succ_ball_receiver_x = element(xs, succ_ball_rec);
        CPIntVar succ_ball_receiver_y = element(ys, succ_ball_rec);
        CPIntVar succ_ball_receiver_w = element(widths, succ_ball_rec);
        CPIntVar succ_ball_receiver_h = element(heights, succ_ball_rec);
        CPIntVar succ_ball_receiver_cls_id = element(cls_ids, succ_ball_rec);
        CPIntVar succ_ball_receiver_track = element(track_ids, succ_ball_rec);


        //the successor comes after
        cp.post(eq(succ_passer_track, plus(passer_track, 1)));
        cp.post(eq(succ_receiver_track, plus(receiver_track, 1)));
        cp.post(eq(succ_ball_receiver_track, plus(ball_receiver_track, 1)));
        cp.post(eq(succ_ball_passer_track, plus(ball_receiver_track, 1)));

        //the predecessor comes before
        cp.post(eq(pred_passer_track, minus(passer_track, 1)));
        cp.post(eq(pred_receiver_track, minus(receiver_track, 1)));
        cp.post(eq(pred_ball_receiver_track, minus(ball_receiver_track, 1)));
        cp.post(eq(pred_ball_passer_track, minus(ball_passer_track, 1)));

        //the ball is of class 'ball'
        cp.post(eq(ball_passer_cls_id, ball));
        cp.post(eq(ball_receiver_cls_id, ball));
        cp.post(eq(succ_ball_passer_cls_id, ball));
        cp.post(eq(succ_ball_receiver_cls_id, ball));
        cp.post(eq(pred_ball_passer_cls_id, ball));
        cp.post(eq(pred_ball_receiver_cls_id, ball));

        int[] players_ids = IntStream.concat(Arrays.stream(players_left), Arrays.stream(players_right))
                .distinct().toArray();


        //the passer and the receiver must be players
        //will become useless with the next constraint anyway
//        for (int i = 1; i <= C; i++) {
//            if (!players.contains(i)) {
//                cp.post(neq(passer_cls_id, i));
//                cp.post(neq(receiver_cls_id, i));
//            }
//        }

        //the passer and the receiver must be on the same team

        //both on the left
        CPBoolVar[] isPasserLeft = new CPBoolVar[players_left.length];
        CPBoolVar[] isReceiverLeft = new CPBoolVar[players_left.length];
        for (int i = 0; i < players_left.length; i++) {
            isPasserLeft[i] = isEq(passer_cls_id, players_left[i]);
            isReceiverLeft[i] = isEq(receiver_cls_id, players_left[i]);
        }

        //both on the right
        CPBoolVar[] isPasserRight = new CPBoolVar[players_right.length];
        CPBoolVar[] isReceiverRight = new CPBoolVar[players_right.length];
        for (int i = 0; i < players_right.length; i++) {
            isPasserRight[i] = isEq(passer_cls_id, players_right[i]);
            isReceiverRight[i] = isEq(receiver_cls_id, players_right[i]);
        }

        CPBoolVar passerLeft = isOr(isPasserLeft);
        CPBoolVar receiverLeft = isOr(isReceiverLeft);
        CPBoolVar passerRight = isOr(isPasserRight);
        CPBoolVar receiverRight = isOr(isReceiverRight);

        CPBoolVar bothLeft = and(passerLeft, receiverLeft);
        CPBoolVar bothRight = and(passerRight, receiverRight);

        cp.post(or(bothLeft, bothRight));

        //but they can't be the same player (dribble)
        cp.post(neq(passer_cls_id, receiver_cls_id));


        //the ball must go from the passer to the receiver
        //to be more robust (avoid the fact that a player can come in front of the ball without being the receiver for instance), we ask that the closeness is for 3 consecutive frames

        //first the ball is close to the passer, i.e. one vertex of the ball is inside the bbox of the player :
        //cp.post(overlapping(pred_passer_x, sum(pred_passer_x, pred_passer_w), pred_passer_y, sum(pred_passer_y, pred_passer_h),
        //        pred_ball_passer_x, sum(pred_ball_passer_x, pred_ball_passer_w), pred_ball_passer_y, sum(pred_ball_passer_y, pred_ball_passer_h),pos_tol));
        cp.post(overlapping(passer_x, sum(passer_x, passer_w), passer_y, sum(passer_y, passer_h),
                ball_passer_x, sum(ball_passer_x, ball_passer_w), ball_passer_y, sum(ball_passer_y, ball_passer_h), pos_tol));
        //cp.post(overlapping(succ_passer_x, sum(succ_passer_x, succ_passer_w), succ_passer_y, sum(succ_passer_y, succ_passer_h),
        //        succ_ball_passer_x, sum(succ_ball_passer_x, succ_ball_passer_w), succ_ball_passer_y, sum(succ_ball_passer_y, succ_ball_passer_h),pos_tol));

        //then the ball is close to the receiver, i.e. one vertex of the ball is inside the bbox of the player :
        cp.post(overlapping(pred_receiver_x, sum(pred_receiver_x, pred_receiver_w), pred_receiver_y, sum(pred_receiver_y, pred_receiver_h),
                pred_ball_receiver_x, sum(pred_ball_receiver_x, pred_ball_receiver_w), pred_ball_receiver_y, sum(pred_ball_receiver_y, pred_ball_receiver_h), pos_tol));
        cp.post(overlapping(receiver_x, sum(receiver_x, receiver_w), receiver_y, sum(receiver_y, receiver_h),
                ball_receiver_x, sum(ball_receiver_x, ball_receiver_w), ball_receiver_y, sum(ball_receiver_y, ball_receiver_h), pos_tol));
        cp.post(overlapping(succ_receiver_x, sum(succ_receiver_x, succ_receiver_w), succ_receiver_y, sum(succ_receiver_y, succ_receiver_h),
                ball_receiver_x, sum(succ_ball_receiver_x, succ_ball_receiver_w), succ_ball_receiver_y, sum(succ_ball_receiver_y, succ_ball_receiver_h), pos_tol));

        //the passer has the ball before the receiver
        cp.post(le(passer_track, receiver_track));

        //the passer_ball/receiver_ball and the passer/receiver must be on the same track
        cp.post(eq(ball_passer_track, passer_track));
        cp.post(eq(ball_receiver_track, receiver_track));

        //the pass can't be too long or too short
        // TODO : replace this by "no player has the ball in between"
        cp.post(le(receiver_track, plus(passer_track, 70)));
        cp.post(ge(receiver_track, plus(passer_track, 20)));

        //no player has the ball in between
        for (int frame = 1; frame < num_tracks; frame++) {
            if (noBall.contains(frame)) continue;
            //is the frame in between the moment the passer passes and the receiver receives ?
            CPBoolVar isInBetween = and(isLt(passer_track, frame), isGt(receiver_track, frame));
            if (isInBetween.isFixed() && isInBetween.contains(0))
                continue; // no need for the rest if we already know we are not in between

            //ball at frame f
            CPIntVar fBall = makeIntVar(cp, n);
            CPIntVar bx = element(xs, fBall);
            CPIntVar by = element(ys, fBall);
            CPIntVar bh = element(heights, fBall);
            CPIntVar bw = element(widths, fBall);
            CPIntVar bcls = element(cls_ids, fBall);
            CPIntVar bframe = element(track_ids, fBall);
            cp.post(eq(bframe, frame));
            cp.post(eq(bcls, ball));
            System.out.println(frame);


            //if it is, then in this frame, no overlapping with any player
            int num_players = players_ids.length;
            CPIntVar[] playersAtF = makeIntVarArray(cp, num_players, n);
            CPBoolVar[] isOverlapping = new CPBoolVar[num_players];
            for (int p = 0; p < num_players; p++) {
                CPIntVar px = element(xs, playersAtF[p]);
                CPIntVar py = element(ys, playersAtF[p]);
                CPIntVar pw = element(widths, playersAtF[p]);
                CPIntVar ph = element(heights, playersAtF[p]);
                CPIntVar pcls = element(cls_ids, playersAtF[p]);
                CPIntVar ptrack = element(track_ids, playersAtF[p]);
                cp.post(eq(ptrack, frame));

                CPBoolVar[] isPlayer = new CPBoolVar[num_players];
                for (int i = 0; i < players_ids.length; i++) {
                    isPlayer[i] = isEq(pcls, players_ids[i]);
                }
                cp.post(or(isPlayer));

                isOverlapping[p] = overlapping(px, sum(px, pw), py, sum(py, ph),
                        bx, sum(bx, bw), by, sum(by, bh), pos_tol);

            }
            CPBoolVar anyoverlap = isOr(isOverlapping);
            cp.post(or(not(isInBetween), not(anyoverlap)));
        }


//        Supplier<Runnable[]> branching = () -> {
//            int[] varval = chooseVar(cp, pattern, track_ids, found);
//            int finalVar = varval[0];
//            int finalVal = varval[1];
//            if (finalVal == -1 | finalVar == -1) return EMPTY;
//            return branch(() -> cp.post(eq(pattern[finalVar], finalVal)),
//                    () -> cp.post(neq(pattern[finalVar], finalVal))
//            );
//        };

        //DFSearch search = makeDfs(cp, branching);
        DFSearch search = makeDfs(cp, Searches.firstFail(pattern));
        Hashtable<Integer, Integer> sol = new Hashtable<>();
        search.onSolution(() -> {
                    System.out.println("solution:" + "PASSER TRACK " + passer_track + " RECEIVER TRACK " + receiver_track + " PATTERN0 VALUE " + pattern[0].max());
                    //found.put(track_ids[pattern[0].min()], track_ids[pattern[1].max()]);
                    if (sol.get(track_ids[pattern[0].max()]) == null) {
                        sol.put(track_ids[pattern[0].max()], track_ids[pattern[1].max()]);
                    } else {
                        int cursol = sol.get(track_ids[pattern[0].max()]);
                        if (track_ids[pattern[1].max()] < track_ids[cursol]) {
                            sol.put(track_ids[pattern[0].max()], track_ids[pattern[1].max()]);
                        }
                    }
                }
        );
        SearchStatistics stats = search.solve();
        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
        System.out.format("Statistics: %s\n", stats);
        System.out.println(sol);
        Hashtable<Integer, Integer> cleaned = clean(sol);
        List<Integer> keys = new ArrayList<>(cleaned.keySet());
        Collections.sort(keys);

        for (int k : keys) {
            System.out.println("frame " + k + "->" + " frame " + cleaned.get(k));
        }


    }

    @Override
    public String getName() {
        return "Pass";
    }

    public int[] chooseVar(CPSolver cp, CPIntVar[] pattern, int[] track_ids, Hashtable<Integer, Integer> found) {
        if (pattern[0].isFixed() && pattern[1].isFixed() && pattern[2].isFixed()) {
            return new int[]{-1, -1};
        }

        int chosenVar = -1;
        int chosenValue = -1;

        for (int varIdx = 0; varIdx < 3; varIdx++) {
            if (pattern[varIdx].isFixed()) continue;
            if (chosenVar != -1) continue;

            if (varIdx == 0) { // the passer
                int[] choices = new int[pattern[varIdx].size()];
                pattern[varIdx].fillArray(choices);
                for (int choice : choices) {
                    Integer chosen_track = track_ids[choice];
                    if (found.get(chosen_track) == null) {
                        chosenValue = choice;
                        chosenVar = varIdx;
                        break;
                    }
                }
            } else {
                chosenVar = varIdx;
                chosenValue = pattern[chosenVar].min();
            }
        }

        if (chosenValue == -1 || chosenVar == -1) {
            return new int[]{-1, -1};
        }

        final int finalVar = chosenVar;
        final int finalVal = chosenValue;

        return new int[]{finalVar, finalVal};
    }

    Hashtable<Integer, Integer> clean(Hashtable<Integer, Integer> sol) {
        // Step 1: sort the keys
        List<Integer> keys = new ArrayList<>(sol.keySet());
        Collections.sort(keys);

        // Step 2: remove consecutive keys (keep only the last)
        List<Integer> filtered = new ArrayList<>();
        Integer prev = null;

        for (int k : keys) {
            if (prev == null || k != prev + 1) {
                // new block
                filtered.add(k);
            } else {
                // consecutive → replace previous end by current key
                filtered.set(filtered.size() - 1, k);
            }
            prev = k;
        }

        // Step 3: remove multiple keys mapping to the same value (keep only the last)
        Map<Integer, Integer> lastForValue = new HashMap<>();
        for (int k : filtered) {
            Integer v = sol.get(k);
            if (v != null) lastForValue.put(v, k);  // overwrite = keep last
        }

        // Step 4: rebuild cleaned hashtable
        Hashtable<Integer, Integer> result = new Hashtable<>();
        for (Map.Entry<Integer, Integer> e : lastForValue.entrySet()) {
            Integer value = e.getKey();
            Integer key = e.getValue();
            result.put(key, value);
        }

        return result;
    }


}