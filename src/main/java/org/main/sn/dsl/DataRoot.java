package org.main.sn.dsl;

import java.nio.file.Path;

/**
 * The folder of the SoccerNet Game State Reconstruction data, the one holding the {@code train},
 * {@code test} and {@code valid} splits (and the {@code scalability} instances). In order of
 * precedence: the system property {@code data.root}, the environment variable {@code DATA_ROOT},
 * or {@code data/SoccerNet/gamestate-2024} relative to the working directory.
 */
public final class DataRoot {

    public static final Path PATH = resolve();

    private DataRoot() {
    }

    private static Path resolve() {
        String property = System.getProperty("data.root");
        if (property != null && !property.isBlank()) return Path.of(property);
        String env = System.getenv("DATA_ROOT");
        if (env != null && !env.isBlank()) return Path.of(env);
        return Path.of("data", "SoccerNet", "gamestate-2024");
    }
}
