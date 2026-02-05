package org.main.sn.dsl;

import java.io.IOException;
import java.util.Arrays;

import static org.main.sn.dsl.Factory.*;

public class Main {

    public static void main(String[] args) throws IOException {

        //ZONE DE TEST

        SelectExpr moveto = SELECT((POSITION(PLAYER("p1",2),4)).MINRANGE(), POSITION(PLAYER("p1",2),10).MINRANGE()).FROM("SNGS-060");
         moveto.search();

        SelectExpr movetoreel = SELECT(PLAYER("p1",2).MOVETO(4,10).MINRANGE()).FROM("SNGS-060");
        movetoreel.search();

        SelectExpr s = SELECT(PLAYER("p2").PASSTO(PLAYER("p3")).MINRANGE()).FROM("SNGS-060");



        // Test passTo
        // -----------

        Player p1 = PLAYER("p1");
        Player p2 = PLAYER("p2");


        Event e1 = p1.PASSTO(p2).WITHIN(150);
        SelectExpr s1 = SELECT(e1).FROM("SNGS-060, SNGS-061"); //toutes les passes
        //s1.search();

        System.out.println("-------------------");

        Event e2 = p2.PASSTO(p1);
        //SelectExpr s2 = sequence("s2", e1, e2).from("all"); //1-2
//        s2.search();

        System.out.println("-------------------");

        Player p19 = PLAYER("p19", 19);
        SelectExpr s3 = SELECT(NOT(p19.PASSTO(p2))).FROM("SNGS-060"); //toutes les passes où 19 n'est pas le passeur
//        s3.search();

        System.out.println("-------------------");

        //test ball move to
        //-----------------

        Ball b = BALL();
        SelectExpr s4 = SELECT(b.MOVETO(4, 6), b.MOVETO(14, 9)).FROM("SNGS-061"); //ball move from zone to zone
        //s4.search();

        System.out.println("-------------------");

//        Player p11 = player("p11", 11);
//        SelectExpr s5 = sequence("s5", and(p11.passTo(p2),not(b.moveTo(4,1)))).from("SNGS-061"); //ball move to zone
//        s5.search();

        System.out.println("-------------------");

        // test has ball
        // -------------

        Player p7 = PLAYER("p7", 7);
        Player p13 = PLAYER("p13", 13);

        SelectExpr s6 = SELECT(POSSESSION(p7)).FROM("SNGS-060"); //p7 has ball for 5 frames
        //s6.search();

        System.out.println("-------------------");

        Event e3 = POSSESSION(p13).RECTANGLE(-31, 39, 31, 20); //p13 has ball in the midfield
        SelectExpr s7 = SELECT(e3).FROM("SNGS-061"); // no result because the possession is not ONLY in the midfield
        //s7.search();

        System.out.println("-------------------");


        //test move to
        //------------

        SelectExpr s8 = SELECT(p1.MOVETO(1, 6), p1.MOVETO(6, 7)).FROM("SNGS-061");
        //s8.search();

        System.out.println("-------------------");


        //test NOT
        //-------


        //test AND
        //-------
        Player p15 = PLAYER("p15", 15);
        //SelectExpr s11 = sequence("s11", and(p13.moveTo(6,7), p15.moveTo(1,2))).from("SNGS-061");
        SelectExpr s11 = SELECT(p13.MOVETO(6, 7)).FROM("SNGS-061").WHERE(START(10),END(740));
        //s11.search();
        SelectExpr s12 = SELECT(p15.MOVETO(1, 2)).FROM("SNGS-061");
        //s12.search();

        SelectExpr s13 = SELECT(AND(p13.MOVETO(6, 7), p15.MOVETO(1, 2))).FROM("SNGS-061");
        //s13.search();


        //test OR
        //-------

        Player p14 = PLAYER("p14", 14);
        SelectExpr s14 = SELECT(p13.MOVETO(6, 7), p13.PASSTO(p14)).FROM("SNGS-061");
        SelectExpr s15 = SELECT(p15.MOVETO(1, 2), p15.PASSTO(p14)).FROM("SNGS-061");


        //test count/atLeast/atMost
        //----------


        //test possession

        SelectExpr s16 = SELECT(NOT(POSSESSION(p2))).FROM("SNGS-060");

    }

}
