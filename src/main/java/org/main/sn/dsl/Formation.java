package org.main.sn.dsl;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Formation {

    private final List<Integer> formation;

    public Formation(List<Integer> lines) {
        if (lines == null || lines.isEmpty())
            throw new IllegalArgumentException("Formation cannot be empty");

        if (lines.stream().anyMatch(i -> i <= 0))
            throw new IllegalArgumentException("All formation numbers must be > 0");

        if (lines.stream().mapToInt(Integer::intValue).sum() != 10){
            throw new IllegalArgumentException("There are 10 players in a formation");
        }

        this.formation = List.copyOf(lines); // immutable copy
    }

    public static Formation of(int... lines) {
        return new Formation(
                Arrays.stream(lines).boxed().toList()
        );
    }

    public List<Integer> lines() {
        return formation;
    }

    @Override
    public String toString() {
        return formation.stream()
                .map(String::valueOf)
                .collect(Collectors.joining("-"));
    }
}
