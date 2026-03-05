package org.main.sn.dsl;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.main.sn.dsl.Factory.*;

public class ScalabilityBenchmark {

    public static void main(String[] args) throws Exception {

        SelectExpr stest = SELECT(
                BALL().MOVETO(4, 1).MINRANGE()).FROM("SNGS-061");
        stest.search();

        System.out.println("TRUNCATED VIDEO BENCHMARK");
        System.out.println("\n");

        //truncatedVideo();

        System.out.println("\n");
        System.out.println("LINEAR VIDEO BENCHMARK");
        System.out.println("\n");



        // Collect match folders from train, test, and valid directories
        String[] splitDirs = {"train", "test", "valid"};
        java.util.List<String> allMatchFolders = new java.util.ArrayList<>();

        for (String split : splitDirs) {
            File dir = new File("data/SoccerNet/gamestate-2024/" + split);
            if (dir.exists() && dir.isDirectory()) {
                String[] folders = dir.list((d, name) -> new File(d, name).isDirectory());
                if (folders != null) {
                    for (String folder : folders) {
                        allMatchFolders.add(folder);
                    }
                }
            }
        }

        if (allMatchFolders.isEmpty()) {
            System.err.println("No match folders found in train/test/valid directories.");
            return;
        }

        java.util.Collections.sort(allMatchFolders); // consistent ordering
        int maxClips = Math.min(90, allMatchFolders.size());
        System.out.println("Found " + allMatchFolders.size() + " total match folders, using " + maxClips + ".");
        System.out.println("Matches,Time(ms)");

        System.out.println("\n");
        System.out.println("Simple Query");
        System.out.println("Matches,Time(s)");

        Player p11 = PLAYER("p11", 11);
        Player p2 = PLAYER("p2");
        Ball b = BALL();

        for (int i = 1; i <= maxClips; i++) {
            // Build the FROM clause with the first i matches
            String fromClause = allMatchFolders.subList(0, i).stream()
                    .collect(Collectors.joining(","));

            long start = System.currentTimeMillis();
            SelectExpr s = SELECT(
                    b.MOVETO(4, 1).MINRANGE()).FROM(fromClause);
            s.search();
            long finish = System.currentTimeMillis();
            double elapsed = (finish - start) / 1000.0;

            System.out.println(i + " , time = " + elapsed);
        }



        System.out.println("\n");
        System.out.println("Middle Query");
        System.out.println("Matches,Time(s)");

        for (int i = 1; i <= maxClips; i++) {
            // Build the FROM clause with the first i matches
            String fromClause = allMatchFolders.subList(0, i).stream()
                    .collect(Collectors.joining(","));

            long start = System.currentTimeMillis();
            SelectExpr s = SELECT(
                    AND(
                            p11.PASSTO(p2).MINRANGE(),
                            NOT(b.MOVETO(4, 1)).MINRANGE()
                    )
            ).FROM(fromClause);
            s.search();
            long finish = System.currentTimeMillis();
            double elapsed = (finish - start) / 1000.0;

            System.out.println(i + " , time = " + elapsed);
        }

        System.out.println("\n");
        System.out.println("Hard Query");
        System.out.println("Matches,Time(s)");

        for (int i = 1; i <= maxClips; i++) {
            // Build the FROM clause with the first i matches
            String fromClause = allMatchFolders.subList(0, i).stream()
                    .collect(Collectors.joining(","));

            long start = System.currentTimeMillis();
            SelectExpr s = SELECT(
                    AND(
                            p11.PASSTO(p2).MINRANGE(),
                            NOT(b.MOVETO(4, 1)).MINRANGE()
                    ),
                    POSSESSION(p11).MINRANGE(),
                    POSITION(p11, 4, 1).MINRANGE()
            ).FROM(fromClause);
            s.search();
            long finish = System.currentTimeMillis();
            double elapsed = (finish - start) / 1000.0;

            System.out.println(i + " , time = " + elapsed);
        }

    }

    public static void truncatedVideo() throws Exception {

        File trainDir = new File("data/SoccerNet/gamestate-2024/scalability");
        if (!trainDir.exists() || !trainDir.isDirectory()) {
            System.err.println("Train directory not found: " + trainDir.getAbsolutePath());
            return;
        }

        String[] matchFolders = trainDir.list((dir, name) -> new File(dir, name).isDirectory());
        if (matchFolders == null || matchFolders.length == 0) {
            System.err.println("No match folders found in " + trainDir.getAbsolutePath());
            return;
        }
        System.out.println("Found " + matchFolders.length + " match folders.");

        Arrays.sort(matchFolders, (a, b1) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b1)));

//        System.out.println("\n");
//        System.out.println("Simple Query");
//        System.out.println("Matches,Time(s)");
//
        Ball b = BALL();
//
//        for (int i = 1; i <= matchFolders.length; i++) {
//            // Build the FROM clause with the first i matches
//            // Build the FROM clause with the first i matches
//            String fromClause = "scalability-" + matchFolders[i - 1];
//
//
//            long start = System.currentTimeMillis();
//            SelectExpr s = SELECT(
//                    b.MOVETO(4, 1).MINRANGE()).FROM(fromClause);
//            s.search();
//            long finish = System.currentTimeMillis();
//            double elapsed = (finish - start) / 1000.0;
//
//            System.out.println(matchFolders[i - 1] + " , time = " + elapsed);
//        }
//
//        System.out.println("\n");
//        System.out.println("Middle Query");
//        System.out.println("Matches,Time(s)");
//
//        Arrays.sort(matchFolders, (a, b1) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b1)));
//
        Player p11 = PLAYER("p11", 11);
        Player p2 = PLAYER("p2");
//
//        for (int i = 1; i <= matchFolders.length; i++) {
//            // Build the FROM clause with the first i matches
//            // Build the FROM clause with the first i matches
//            String fromClause = "scalability-" + matchFolders[i - 1];
//
//
//            long start = System.currentTimeMillis();
//            SelectExpr s = SELECT(
//                    AND(
//                            p11.PASSTO(p2).MINRANGE(),
//                            NOT(b.MOVETO(4, 1)).MINRANGE()
//                    )
//            ).FROM(fromClause);
//            s.search();
//            long finish = System.currentTimeMillis();
//            double elapsed = (finish - start) / 1000.0;
//
//            System.out.println(matchFolders[i - 1] + " , time = " + elapsed);
//        }

        System.out.println("\n");
        System.out.println("Hard Query");
        System.out.println("Matches,Time(s)");

        Arrays.sort(matchFolders, (a, b1) -> Integer.compare(Integer.parseInt(a), Integer.parseInt(b1)));

        for (int i = 1; i <= matchFolders.length; i++) {
            // Build the FROM clause with the first i matches
            // Build the FROM clause with the first i matches
            String fromClause = "scalability-" + matchFolders[i - 1];


            long start = System.currentTimeMillis();
            SelectExpr s = SELECT(
                    AND(
                            p11.PASSTO(p2).MINRANGE(),
                            NOT(b.MOVETO(4, 1)).MINRANGE()
                    ),
                    POSSESSION(p11).MINRANGE(),
                    POSITION(p11, 4, 1).MINRANGE()
            ).FROM(fromClause);
            s.search();
            long finish = System.currentTimeMillis();
            double elapsed = (finish - start) / 1000.0;

            System.out.println(matchFolders[i - 1] + " , time = " + elapsed);
        }
    }
}
