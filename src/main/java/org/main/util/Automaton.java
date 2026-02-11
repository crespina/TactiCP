package org.main.util;

import java.util.Arrays;

public class Automaton {

    private Automaton() {
    }


    public static final int[][] A_0STAR_B(int nPlayers) {
        //n is the number of possible inputs excluding 0 (i.e. the number of players)
        //ACCEPTING STATE IS nPlayers+1

        int inputs = nPlayers + 1;
        int states = nPlayers + 2;
        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        int start = 0;
        int accept = nPlayers + 1;

        // from start: only non-zero symbols can begin
        for (int a = 1; a <= nPlayers; a++) {
            res[start][a] = a;
        }

        // zero : loop
        // non-zero : b != A -> accept, b == A -> invalid
        for (int a = 1; a <= nPlayers; a++) {
            res[a][0] = a;
            for (int b = 1; b <= nPlayers; b++) {
                if (b != a) {
                    res[a][b] = accept;
                }
            }
        }

        return res;
    }

    public static final int[][] A_0PLUS_B(int nPlayers) {
        //n is the number of possible inputs excluding 0 (i.e. the number of players)
        //ACCEPTING STATE IS 2*nPlayers+1

        int inputs = nPlayers + 1;
        int states = 2 * nPlayers + 2;
        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        int start = 0;
        int accept = 2 * nPlayers + 1;

        // from start: only non-zero symbols can begin
        for (int a = 1; a <= nPlayers; a++) {
            res[start][a] = a;
        }

        // at least one zero
        for (int a = 1; a <= nPlayers; a++) {
            res[a][0] = nPlayers + a;   // first zero -> go to zero-seen state
        }

        // zero : loop
        // non-zero : b != A -> accept, b == A -> invalid
        for (int a = 1; a <= nPlayers; a++) {
            res[nPlayers + a][0] = nPlayers + a;
            for (int b = 1; b <= nPlayers; b++) {
                if (b != a) {
                    res[nPlayers + a][b] = accept;
                }
            }
        }

        return res;
    }

    public static int[][] APLUS_0STAR_BPLUS(int nPlayers) {
        //n is the number of possible inputs excluding 0 (i.e. the number of players)
        // ACCEPTING STATES ARE 2*nPlayers+1, 2*nPlayers+2, ..., 3*nPlayers (one for each player)

        int inputs = nPlayers + 1;
        int states = 3 * nPlayers + 1;
        int[][] res = new int[states][inputs];

        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        int start = 0;

        // from start: only non-zero symbols can begin
        for (int a = 1; a <= nPlayers; a++) {
            res[start][a] = a;
        }
        // can repeat As (loop) or go to 0 phase or go to B
        for (int a = 1; a <= nPlayers; a++) {
            res[a][a] = a; // more As
            res[a][0] = nPlayers + a; // go to 0* phase

            for (int b = 1; b <= nPlayers; b++) {
                if (b != a) {
                    res[a][b] = 2 * nPlayers + a; // directly to B+ (no zeros case)
                }
            }
        }

        // 0s can repeat (loop) or go to B
        for (int a = 1; a <= nPlayers; a++) {
            res[nPlayers + a][0] = nPlayers + a;  // stay on zeros

            for (int b = 1; b <= nPlayers; b++) {
                if (b != a) {
                    res[nPlayers + a][b] = 2 * nPlayers + a; // start B+
                }
            }
        }

        // can repeat Bs (loop) or go to accept, but cannot go back to A or 0
        for (int a = 1; a <= nPlayers; a++) {
            for (int b = 1; b <= nPlayers; b++) {
                if (b != a) {
                    res[2 * nPlayers + a][b] = 2 * nPlayers + a;   // keep reading same B
                }
            }
        }

        return res;
    }


    public static int[][] APLUS_0PLUS_BPLUS(int nPlayers) {
        //n is the number of possible inputs excluding 0 (i.e. the number of players)
        // ACCEPTING STATES ARE 2*nPlayers+1, 2*nPlayers+2, ..., 3*nPlayers (one for each player)

        int inputs = nPlayers + 1;
        int states = 3 * nPlayers + 1;
        int[][] res = new int[states][inputs];

        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        int start = 0;

        // from start: only non-zero symbols can begin
        for (int a = 1; a <= nPlayers; a++) {
            res[start][a] = a;
        }
        // can repeat As (loop) or go to 0 phase or go to B
        for (int a = 1; a <= nPlayers; a++) {
            res[a][a] = a; // more As
            res[a][0] = nPlayers + a; // go to 0* phase
        }

        // 0s can repeat (loop) or go to B
        for (int a = 1; a <= nPlayers; a++) {
            res[nPlayers + a][0] = nPlayers + a;  // stay on zeros

            for (int b = 1; b <= nPlayers; b++) {
                if (b != a) {
                    res[nPlayers + a][b] = 2 * nPlayers + a; // start B+
                }
            }
        }

        // can repeat Bs (loop) or go to accept, but cannot go back to A or 0
        for (int a = 1; a <= nPlayers; a++) {
            for (int b = 1; b <= nPlayers; b++) {
                if (b != a) {
                    res[2 * nPlayers + a][b] = 2 * nPlayers + a;   // keep reading same B
                }
            }
        }

        return res;
    }
}
