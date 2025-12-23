package org.main.sn.dsl;

import static org.main.sn.dsl.Factory.player;

public class Main {

    public static void main(String[] args) {
        Player p1 = player("p1");
        Player p2 = player("p2");
        Player p3 = player("p1", 15);

        System.out.println(p1 == p3);
    }
}
