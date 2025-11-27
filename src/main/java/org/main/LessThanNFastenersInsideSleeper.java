package org.main;

import java.util.Arrays;

import org.maxicp.cp.engine.core.CPBoolVar;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.Searches;
import org.maxicp.util.exception.InconsistencyException;

import static org.maxicp.cp.CPFactory.*;

/*
    Returns a solution if not enough fasteners are detected inside a sleeper
 */

public class LessThanNFastenersInsideSleeper implements ConstraintPattern {

    private final int N;
    private final int num_tol;
    private final int pos_tol;

    public LessThanNFastenersInsideSleeper(int N, int num_tol, int pos_tol) {
        this.N = N; // expected number of fasteners inside a sleeper
        this.num_tol = num_tol; // tolerance on the number of fasteners inside a sleeper
        this.pos_tol = pos_tol; // tolerance on the positions of bboxes
    }

    @Override
    public void apply(CPSolver cp, Instance instance) {
        //Create instance
        TSVInstance tsv = (TSVInstance) instance;
        int n = tsv.n; // number of bboxes
        int[] x_mins = tsv.x_mins;
        int[] x_maxs = tsv.x_maxs;
        int[] y_mins = tsv.y_mins;
        int[] y_maxs = tsv.y_maxs;
        int[] cls_ids = tsv.cls_ids;
        int[] box_ids = tsv.box_ids;

        int tolerated_N = N - num_tol;

        //Create variables : pattern to be matched
        CPIntVar[] pattern = makeIntVarArray(cp, tolerated_N + 1, n); // pattern[0] represents the sleeper (outside) and pattern[1] represents the fastener (inside)

        CPIntVar sleeper_x1 = element(x_mins, pattern[0]);
        CPIntVar sleeper_x2 = element(x_maxs, pattern[0]);
        CPIntVar sleeper_y1 = element(y_mins, pattern[0]);
        CPIntVar sleeper_y2 = element(y_maxs, pattern[0]);
        CPIntVar sleeper_cls_id = element(cls_ids, pattern[0]);
        CPIntVar sleeper_box_id = element(box_ids, pattern[0]);

        cp.post(eq(sleeper_cls_id, 1));

        CPBoolVar[] isInside = makeBoolVarArray(cp, tolerated_N);

        for (int i = 1; i <= tolerated_N; i++) {
            CPIntVar fastener_x1 = element(x_mins, pattern[i]);
            CPIntVar fastener_x2 = element(x_maxs, pattern[i]);
            CPIntVar fastener_y1 = element(y_mins, pattern[i]);
            CPIntVar fastener_y2 = element(y_maxs, pattern[i]);
            CPIntVar fastener_cls_id = element(cls_ids, pattern[i]);

            cp.post(eq(fastener_cls_id, 0));

            //isInside[i];
            CPBoolVar isx1 = isGe(fastener_x1, minus(sleeper_x1, pos_tol));
            CPBoolVar isy1 = isGe(fastener_y1, minus(sleeper_y1, pos_tol));
            CPBoolVar isx2 = isLe(fastener_x2, plus(sleeper_x2, pos_tol));
            CPBoolVar isy2 = isLe(fastener_y2, plus(sleeper_y2, pos_tol));


            isInside[i - 1] = not(isOr(not(isx1), not(isx2), not(isy2), not(isy1)));
        }

        cp.post(ge(sum(isInside), tolerated_N));

        cp.post(allDifferent(pattern));

        // Symmetry breaking: enforce ascending order for fasteners
        for (int i = 1; i < tolerated_N; i++) {
            cp.post(lt(pattern[i], pattern[i + 1]));
        }

        int[] possible_index = new int[n];
        Arrays.fill(possible_index, -1);
        pattern[0].fillArray(possible_index);
        int[] filtered_values = Arrays.stream(possible_index)
                .filter(i -> i != -1)
                .toArray();

        for (int i : filtered_values) {
            cp.getStateManager().saveState();
            try {
                cp.post(eq(pattern[0], i));
                DFSearch search = makeDfs(cp, Searches.firstFail(pattern));
                search.onSolution(() -> {
                });
                search.solve(searchStatistics -> searchStatistics.numberOfSolutions() == 1);

            } catch (InconsistencyException e) {

                System.out.println("Not enough fasteners for sleeper " + sleeper_box_id);
            } finally {
                cp.getStateManager().restoreState();
            }
        }

    }

    @Override
    public String getName() {
        return "LeqNInside";
    }
}