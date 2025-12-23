package org.main.sn.dsl;

public interface Pattern {
    void search();
    Pattern within(double duration);
    void from(String... ids); //matches, dataset
}
