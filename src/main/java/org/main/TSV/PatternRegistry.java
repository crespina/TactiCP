package org.main.TSV;

import org.main.util.ConstraintPattern;

import java.util.Map;
import java.util.HashMap;
import java.util.function.Function;

public class PatternRegistry {
    private static final Map<String, Function<Map<String, String>, ConstraintPattern>> patterns = new HashMap<>();

    static {
        patterns.put("Inside", args ->
                new FastenerInsideSleeper(Integer.parseInt(args.getOrDefault("pos_tol", "20000"))));

        patterns.put("NInside", args ->
                new NFastenersInsideSleeper(
                        Integer.parseInt(args.getOrDefault("N", "12")),
                        Integer.parseInt(args.getOrDefault("pos_tol", "20000"))
                ));

        patterns.put("NInsideTol", args ->
                new NFastenersInsideSleeperWTol(
                        Integer.parseInt(args.getOrDefault("N", "12")),
                        Integer.parseInt(args.getOrDefault("num_tol", "2")),
                        Integer.parseInt(args.getOrDefault("pos_tol", "20000"))
                ));

        patterns.put("LTNInsideTol", args ->
                new LessThanNFastenersInsideSleeper(
                        Integer.parseInt(args.getOrDefault("N", "12")),
                        Integer.parseInt(args.getOrDefault("num_tol", "3")),
                        Integer.parseInt(args.getOrDefault("pos_tol", "20000"))
                ));
    }

    public static ConstraintPattern create(String name, Map<String, String> args) {
        Function<Map<String, String>, ConstraintPattern> factory = patterns.get(name);
        if (factory == null) throw new IllegalArgumentException("Unknown pattern: " + name);
        return factory.apply(args);
    }
}
