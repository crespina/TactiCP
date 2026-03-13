package org.main.sn.experiments;

import org.main.sn.dsl.SelectExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import static org.main.sn.dsl.Factory.*;

public class PassPrediction {

    public static void main(String[] args) throws IOException {
        Path trainDir = Paths.get(
                System.getProperty("user.home"),
                "GeometricPatternMatching", "data", "SoccerNet", "gamestate-2024").resolve("train"); // same ROOT used by FROM(...)
        try (var paths = Files.list(trainDir)) {
            paths.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .forEach(folder -> {
                        String sngsId = folder.getFileName().toString(); // e.g. SNGS-060
                        try {
                            double startTime = System.currentTimeMillis();

                            SelectExpr pass = SELECT(PLAYER("p1").PASSTO(PLAYER("p2"))).FROM(sngsId);
                            int count = pass.COUNT();

                            double endTime = System.currentTimeMillis();
                            System.out.println(
                                    sngsId + " -> " + count + " matches in " + (endTime - startTime) + " ms"
                            );
                        } catch (Exception e) {
                            System.err.println("Failed on " + sngsId + ": " + e.getMessage());
                        }
                    });
        }
    }
}
