package org.main.sn;

import org.main.util.ConstraintPattern;
import org.main.util.Instance;
import org.maxicp.cp.engine.constraints.TableCT;
import org.maxicp.cp.engine.core.CPIntVar;
import org.maxicp.cp.engine.core.CPSolver;
import org.maxicp.search.DFSearch;
import org.maxicp.search.SearchStatistics;
import org.maxicp.search.Searches;

import java.util.*;

import static org.maxicp.cp.CPFactory.*;

public class Pass implements ConstraintPattern {

    public Pass() {
    }

    @Override
    public void apply(CPSolver cp, Instance instance) {

        if (instance instanceof TrackingInstance soccer) {

            int n = soccer.n;
            int[] players_right_idx = soccer.players_right_idx;
            int[] players_left_idx = soccer.players_left_idx;

            Possession p = new Possession();
            p.apply(cp, soccer);
            int[] possession = p.result;

            //variables = frame_start and frame_end
            CPIntVar start = makeIntVar(cp, n);
            CPIntVar end = makeIntVar(cp, n);

            //cp.post(le(start,end)); already in NoInBetween

            CPIntVar player_start = element(possession, start);
            CPIntVar player_end = element(possession, end);

            cp.post(neq(player_start, player_end)); //2 different players

            // both player_start and player_end needs to be in the same array players_right_idx
            List<int[]> rows = new ArrayList<>();

            for (int a : players_left_idx) {
                for (int b : players_left_idx) {
                    rows.add(new int[]{a, b});
                }
            }
            for (int a : players_right_idx) {
                for (int b : players_right_idx) {
                    rows.add(new int[]{a, b});
                }
            }

            int[][] table = new int[rows.size()][2];
            for (int i = 0; i < rows.size(); i++) table[i] = rows.get(i);

            // create TableCT on the two vars
            CPIntVar[] scope = new CPIntVar[]{player_start, player_end};
            cp.post(new TableCT(scope, table));

            cp.post(new NoInBetween(possession, start, end)); //no possession between start and end


            DFSearch search = makeDfs(cp, Searches.firstFail(start, end));



            Hashtable<PlayerAtFrame, PlayerAtFrame> sol = new Hashtable<>();
            search.onSolution(() -> {

                        PlayerAtFrame passer = new PlayerAtFrame(player_start.max(),start.max());
                        PlayerAtFrame receiver = new PlayerAtFrame(player_end.max(),end.max());
                        PlayerAtFrame old = sol.get(passer);
                        if (old == null || receiver.frameId < old.frameId) {
                            sol.put(passer, receiver);
                        }
                    }
            );

            SearchStatistics stats = search.solve();
            System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
            System.out.format("Statistics: %s\n", stats);
            sol.keySet().stream()
                    .sorted(Comparator.comparingInt(PlayerAtFrame::frameId))
                    .forEach(k -> {
                        PlayerAtFrame v = sol.get(k);
                        System.out.println(k.frameId() + " -> " + v.frameId());
                    });



        } else if (instance instanceof GameStateReconstructionInstance soccer) {
            //Create instance
            int[] teams = soccer.teams;

            int n = soccer.n;

            Possession p = new Possession();
            p.apply(cp, instance);
            int[] possession = p.result;


            //variables = frame_start and frame_end
            CPIntVar start = makeIntVar(cp, n);
            CPIntVar end = makeIntVar(cp, n);

            //cp.post(le(start,end)); already in NoInBetween

            CPIntVar player_start = element(possession, start);
            CPIntVar player_end = element(possession, end);

            cp.post(neq(player_start, player_end)); //2 different players
            cp.post(eq(element(teams, player_start), element(teams, player_end))); // in the same team
            cp.post(new NoInBetween(possession, start, end)); //no possession between start and end


            //search
            DFSearch search = makeDfs(cp, Searches.firstFail(start, end));
            Hashtable<PlayerAtFrame, PlayerAtFrame> sol = new Hashtable<>();
            search.onSolution(() -> {

                        PlayerAtFrame passer = new PlayerAtFrame(player_start.max(),start.max());
                        PlayerAtFrame receiver = new PlayerAtFrame(player_end.max(),end.max());
                        PlayerAtFrame old = sol.get(passer);
                        if (old == null || receiver.frameId < old.frameId) {
                            sol.put(passer, receiver);
                        }
                    }
            );
            SearchStatistics stats = search.solve();
            System.out.format("#Solutions: %s\n", stats.numberOfSolutions());
            System.out.format("Statistics: %s\n", stats);
            sol.keySet().stream()
                    .sorted(Comparator.comparingInt(PlayerAtFrame::frameId))
                    .forEach(k -> {
                        PlayerAtFrame v = sol.get(k);
                        System.out.println(k.frameId() + " -> " + v.frameId());
                    });

        }

    }
    public static record PlayerAtFrame(int playerId, int frameId) {}

    @Override
    public String getName() {
        return "Pass";
    }



}