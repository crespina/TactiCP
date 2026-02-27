//package org.main.TSV;
//
//import org.main.util.ConstraintPattern;
//import org.main.util.Instance;
//import org.maxicp.cp.CPFactory;
//import org.maxicp.cp.engine.core.CPIntVar;
//import org.maxicp.cp.engine.core.CPSolver;
//import org.maxicp.search.DFSearch;
//import org.maxicp.search.SearchStatistics;
//import org.maxicp.search.Searches;
//
//import static org.maxicp.cp.CPFactory.*;
//
//public class NFastenersInsideSleeper implements ConstraintPattern {
//
//    private final int N; // number of fasteners inside a sleeper
//    private final int pos_tol; // tolerance on the positions of bboxes
//
//    public NFastenersInsideSleeper(int N, int pos_tol) {
//        this.N = N;
//        this.pos_tol = pos_tol;
//    }
//
//    @Override
//    public void apply(CPSolver cp, Instance instance) {
//        //Create instance
//        TSVInstance tsv = (TSVInstance) instance;
//        int n = tsv.n; // number of bboxes
//        int[] x_mins = tsv.x_mins;
//        int[] x_maxs = tsv.x_maxs;
//        int[] y_mins = tsv.y_mins;
//        int[] y_maxs = tsv.y_maxs;
//        int[] cls_ids = tsv.cls_ids;
//        int[] box_ids = tsv.box_ids;
//
//        //Create variables : pattern to be matched
//        CPIntVar[] pattern = makeIntVarArray(cp, N + 1, n); // pattern[0] represents the sleeper (outside) and pattern[1] represents the fastener (inside)
//
//        CPIntVar sleeper_x1 = element(x_mins, pattern[0]);
//        CPIntVar sleeper_x2 = element(x_maxs, pattern[0]);
//        CPIntVar sleeper_y1 = element(y_mins, pattern[0]);
//        CPIntVar sleeper_y2 = element(y_maxs, pattern[0]);
//        CPIntVar sleeper_cls_id = element(cls_ids, pattern[0]);
//        CPIntVar sleeper_box_id = element(box_ids, pattern[0]);
//
//        cp.post(eq(sleeper_cls_id, 1));
//
//        for (int i = 1; i <= N; i++) {
//            CPIntVar fastener_x1 = element(x_mins, pattern[i]);
//            CPIntVar fastener_x2 = element(x_maxs, pattern[i]);
//            CPIntVar fastener_y1 = element(y_mins, pattern[i]);
//            CPIntVar fastener_y2 = element(y_maxs, pattern[i]);
//            CPIntVar fastener_cls_id = element(cls_ids, pattern[i]);
//            CPIntVar fastener_box_id = element(box_ids, pattern[i]);
//
//            cp.post(eq(fastener_cls_id, 0));
//
//            cp.post(ge(fastener_x1, minus(sleeper_x1, pos_tol)));
//            cp.post(ge(fastener_y1, minus(sleeper_y1, pos_tol)));
//            cp.post(le(fastener_x2, plus(sleeper_x2, pos_tol)));
//            cp.post(le(fastener_y2, plus(sleeper_y2, pos_tol)));
//
//        }
//
//        cp.post(allDifferent(pattern));
//
//        // Symmetry breaking: enforce ascending order for fasteners
//        for (int i = 1; i < N; i++) {
//            cp.post(lt(pattern[i], pattern[i + 1]));
//        }
//
//        DFSearch search = CPFactory.makeDfs(cp, Searches.firstFail(pattern));
//
//        search.onSolution(() -> {
//            System.out.print("Sleeper ID: " + sleeper_box_id + " -> Fasteners: ");
//            for (int i = 1; i <= N; i++) {
//                CPIntVar fastener_box_id = element(box_ids, pattern[i]);
//                System.out.print(fastener_box_id + " ");
//            }
//            System.out.println();
//        });
//
//        SearchStatistics stats = search.solve();
//        System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
//        System.out.format("Statistics: %s\n", stats);
//
//    }
//
//    @Override
//    public String getName() {
//        return "NInside";
//    }
//}