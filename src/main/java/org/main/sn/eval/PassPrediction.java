package org.main.sn.experiments;

import org.main.sn.dsl.SelectExpr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;

import static org.main.sn.dsl.Factory.*;

public class PassPrediction {

    public static void main(String[] args) throws IOException {
        Path rootDir = Paths.get(
                System.getProperty("user.home"),
                "GeometricPatternMatching", "data", "SoccerNet", "gamestate-2024"
        );

        List<String> splits = List.of("train", "test", "valid");

        for (String split : splits) {
            Path splitDir = rootDir.resolve(split);
            if (!Files.isDirectory(splitDir)) {
                System.err.println("Skipping missing split directory: " + splitDir);
                continue;
            }

            System.out.println("=== Split: " + split + " ===");

            try (var paths = Files.list(splitDir)) {
                paths.filter(Files::isDirectory)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .forEach(folder -> {
                            String sngsId = folder.getFileName().toString(); // e.g. SNGS-060
                            try {
                                double startTime = System.currentTimeMillis();

                                SelectExpr pass = SELECT(PLAYER("p1").PASSTO(PLAYER("p2")).MINRANGE()).FROM(sngsId);
                                pass.searchAndPrint();

                                double endTime = System.currentTimeMillis();
                                System.out.println(
                                        split + "/" + sngsId + " -> " + (endTime - startTime) + " ms"
                                );
                            } catch (Exception e) {
                                System.err.println("Failed on " + split + "/" + sngsId + ": " + e.getMessage());
                            }
                        });
            }
        }
    }
}
