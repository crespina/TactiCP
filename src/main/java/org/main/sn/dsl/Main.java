package org.main.sn.dsl;

import java.io.IOException;

import static org.main.sn.dsl.Factory.*;

public class Main {

    public static void main(String[] args) throws IOException {

        // Test passTo
        // -----------

        Player p1 = player("p1");
        Player p2 = player("p2");


        Event e1 = p1.passTo(p2);
        Sequence s1 = sequence("s1", e1).from("SNGS-060, SNGS-061"); //toutes les passes
        //s1.search();

        System.out.println("-------------------");

        Event e2 = p2.passTo(p1);
        //Sequence s2 = sequence("s2", e1, e2).from("all"); //1-2
//        s2.search();

        System.out.println("-------------------");

        Player p19 = player("p19", 19);
        Sequence s3 = sequence("seq1", not(p19.passTo(p2))).from("SNGS-060"); //toutes les passes où 19 n'est pas le passeur
//        s3.search();

        System.out.println("-------------------");

        //test ball move to
        //-----------------

        Ball b = ball();
        Sequence s4 = sequence("s4", b.moveTo(4, 6), b.moveTo(14, 9)).from("SNGS-061"); //ball move from zone to zone
//        s4.search();

        System.out.println("-------------------");

//        Player p11 = player("p11", 11);
//        Sequence s5 = sequence("s5", and(p11.passTo(p2),not(b.moveTo(4,1)))).from("SNGS-061"); //ball move to zone
//        s5.search();

        System.out.println("-------------------");

        // test has ball
        // -------------

        Player p7 = player("p7", 7);
        Player p13 = player("p13", 13);

        Sequence s6 = sequence("s6", p7.hasBall()).from("SNGS-060"); //p7 has ball for 5 frames
        //s6.search();

        System.out.println("-------------------");

        Event e3 = p13.hasBall().rectangle(-31,39,31,20); //p13 has ball in the midfield
        Sequence s7 = sequence("s7", e3).from("SNGS-061"); // no result because the possession is not ONLY in the midfield
        //s7.search();

        System.out.println("-------------------");


        //test move to
        //------------

        Sequence s8 = sequence("s8", p1.moveTo(1,6), p1.moveTo(6,7)).from("SNGS-061");
        //s8.search();

        System.out.println("-------------------");


        //test isInZones
        //--------------

        Team t1 = team("t1", 5,11,16,14);
        Sequence s9 = sequence("s9", t1.isInZones(4,6)).from("SNGS-061");
        //s9.search();

        System.out.println("-------------------");

        Team t2 = team("t2", 7,15,18,9,8,17);
        Sequence s10 = sequence("s10", t2.isInZones(4,11)).from("SNGS-061");
        //s10.search();

        System.out.println("-------------------");

        //test NOT
        //-------
        //TODO: think about the meaning of not



        //test AND
        //-------
        Player p15 = player("p15", 15);
        //Sequence s11 = sequence("s11", and(p13.moveTo(6,7), p15.moveTo(1,2))).from("SNGS-061");
        Sequence s11 = sequence("s11", p13.moveTo(6,7)).from("SNGS-061");
        //s11.search();
        Sequence s12 = sequence("s12", p15.moveTo(1,2)).from("SNGS-061");
        //s12.search();

        Sequence s13 = sequence("s13", and(p13.moveTo(6,7), p15.moveTo(1,2))).from("SNGS-061");
        //s13.search();


        //test OR
        //-------

        Player p14 = player("p14", 14);
        Sequence s14 = sequence("s14", p13.moveTo(6,7),p13.passTo(p14)).from("SNGS-061");
        Sequence s15 = sequence("s15", p15.moveTo(1,2), p15.passTo(p14)).from("SNGS-061");

        Sequence s16 = sequence("s16", or())


        //test count/atLeast/atMost
        //----------

        count(s1);
        atLeast(s1,10);
        atMost(s1,5);
    }
}
