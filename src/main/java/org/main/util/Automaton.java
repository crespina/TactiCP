package org.main.util;

import java.util.*;

public class Automaton {

    int initState;
    List<Integer> acceptingStates;
    int[][] automaton;

    public Automaton(int initState, List<Integer> acceptingStates, int[][] automaton) {
        this.initState = initState;
        this.acceptingStates = acceptingStates;
        this.automaton = automaton;
    }

    public int[][] getAutomaton() {
        return automaton;
    }

    public List<Integer> getAcceptingStates() {
        return acceptingStates;
    }

    public int getInitState() {
        return initState;
    }

    //minimal pass
    public static Automaton A_0STAR_B(int nPlayers) {
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

        return new Automaton(0, List.of(accept), res);
    }

    //minimal pass
    public static Automaton A_0STAR_B(int nPlayers, Set<Integer> As, Set<Integer> Bs) {
        //n is the number of possible inputs excluding 0 (i.e. the number of players)
        //ACCEPTING STATE IS nPlayers+1
        for (int x : As) {
            if (x < 1 || x > nPlayers) throw new IllegalArgumentException("validAs contains out-of-range value: " + x);
        }
        for (int x : Bs) {
            if (x < 1 || x > nPlayers) throw new IllegalArgumentException("validBs contains out-of-range value: " + x);
        }

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
            if (As.contains(a)) {
                res[start][a] = a;
            }
        }

        // zero : loop
        // non-zero : b != A -> accept, b == A -> invalid
        for (int a = 1; a <= nPlayers; a++) {
            if (!As.contains(a)) {
                continue;
            }
            res[a][0] = a;
            for (int b = 1; b <= nPlayers; b++) {
                if (Bs.contains(b) && b != a) {
                    res[a][b] = accept;
                }
            }
        }

        return new Automaton(0, List.of(accept), res);
    }

    // A 0+ B  (one or more zeros required between A and B)
    public static Automaton A_0PLUS_B(int nPlayers, Set<Integer> As, Set<Integer> Bs) {
        for (int x : As) {
            if (x < 1 || x > nPlayers) throw new IllegalArgumentException("As contains out-of-range value: " + x);
        }
        for (int x : Bs) {
            if (x < 1 || x > nPlayers) throw new IllegalArgumentException("Bs contains out-of-range value: " + x);
        }

        int inputs = nPlayers + 1;
        // States:
        //   0               → start
        //   1..nPlayers     → read A=a, no zeros seen yet  (layer 1)
        //   nPlayers+1..2*nPlayers → read A=a, ≥1 zero seen (layer 2)
        //   2*nPlayers+1    → accept
        int states = 2 * nPlayers + 2;
        int accept = 2 * nPlayers + 1;

        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) Arrays.fill(res[i], -1);

        // From start: only valid A symbols
        for (int a = 1; a <= nPlayers; a++) {
            if (As.contains(a)) {
                res[0][a] = a; // → layer-1 state for 'a'
            }
        }

        for (int a = 1; a <= nPlayers; a++) {
            if (!As.contains(a)) continue;

            int layer1 = a;               // waiting for first 0
            int layer2 = nPlayers + a;    // ≥1 zero seen

            // Layer 1: only 0 advances us; anything else is a dead end
            res[layer1][0] = layer2;

            // Layer 2: 0 loops; valid B (≠ a) goes to accept
            res[layer2][0] = layer2;
            for (int b = 1; b <= nPlayers; b++) {
                if (Bs.contains(b) && b != a) {
                    res[layer2][b] = accept;
                }
            }
        }

        return new Automaton(0, List.of(accept), res);
    }

    //minimal movement
    public static Automaton A_NOTBSTAREXCEPT0_B(int nPlayers, int A, int B) {
        //n is the number of possible inputs excluding 0 (i.e. the number of players); A and B are the start and the end respectively
        //ACCEPTING STATE IS 2

        int inputs = nPlayers + 1;
        int states = 3;
        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        int start = 0;
        int accept = 2;

        // from start: only A can begin
        res[start][A] = 1;

        // not b : loop
        // b : accept
        // 0 or S : invalid
        for (int i = 0; i <= nPlayers; i++) {
            if (i == B) {
                res[1][i] = accept;
            } else if (i != A && i != 0) { //loop
                res[1][i] = 1;
            }
        }

        return new Automaton(0, List.of(accept), res);
    }

    //minimal movement
    public static Automaton A_NOTBSTAR_B(int nPlayers, int A, int B) {
        //n is the number of possible inputs excluding 0 (i.e. the number of players); A and B are the start and the end respectively
        //ACCEPTING STATE IS 2

        int inputs = nPlayers + 1;
        int states = 3;
        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        int start = 0;
        int accept = 2;

        // from start: only A can begin
        res[start][A] = 1;

        // not b : loop
        // b : accept
        // 0 or S : invalid
        for (int i = 0; i <= nPlayers; i++) {
            if (i == B) {
                res[1][i] = accept;
            } else if (i != A && i != 0) { //loop
                res[1][i] = 1;
            }
        }

        return new Automaton(0, List.of(accept), res);
    }

    //extended movement
    public static Automaton APLUS_NOTB_STAR_BPLUS(int nPlayers, int A, int B) {
        //n is the number of possible inputs excluding 0 (i.e. the number of players); A and B are the start and the end respectively
        //ACCEPTING STATE IS 2

        int inputs = nPlayers + 1;
        int states = 3;
        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        int start = 0;
        int accept = 2;

        // from start: only A can begin
        res[start][A] = 1;

        // not b : loop
        // b : accept
        // 0 or S : invalid
        for (int i = 0; i <= nPlayers; i++) {
            if (i == A) {
                res[1][i] = 1; // can repeat A
            } else if (i == B) {
                res[1][i] = accept;
            } else if (i != 0) { //loop
                res[1][i] = 1;
            }
        }

        res[accept][B] = accept;  // allow B repetition

        return new Automaton(0, List.of(accept), res);
    }

    public static Automaton PAD_APLUS_PADSTAR_BPLUS_PAD(int nPlayers, int A, int B) {
        int inputs = nPlayers + 1; // symbols 0...nPlayers
        int states = 6;
        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) Arrays.fill(res[i], -1);

        int start = 0, afterLead = 1, Astate = 2, midPad = 3, Bstate = 4, accept = 5;

        // from start: any not{A} (including 0) -> afterLead
        for (int x = 0; x <= nPlayers; x++) {
            if (x != A) res[start][x] = afterLead;
        }

        // afterLead: must see A to begin A+
        if (A >= 0 && A <= nPlayers) res[afterLead][A] = Astate;

        // Astate: A -> stay in Astate; B -> go to Bstate; any not{A,B} -> midPad
        for (int x = 0; x <= nPlayers; x++) {
            if (x == A) res[Astate][x] = Astate;
            else if (x == B) res[Astate][x] = Bstate;
            else res[Astate][x] = midPad; // includes 0
        }

        // midPad: loop on not{A,B}; on B -> Bstate; A is invalid here
        for (int x = 0; x <= nPlayers; x++) {
            if (x == B) res[midPad][x] = Bstate;
            else if (x != A && x != B) res[midPad][x] = midPad;
        }

        // Bstate: B -> stay; not{B} -> accept
        for (int x = 0; x <= nPlayers; x++) {
            if (x == B) res[Bstate][x] = Bstate;
            else if (x != B) res[Bstate][x] = accept;
        }

        return new Automaton(start, List.of(accept), res);
    }

    public static Automaton PAD_APLUS_PADSTAR_BPLUS_PAD(int nPlayers, Set<Integer> As, Set<Integer> Bs) {
        int inputs = nPlayers + 1; // symbols 0...nPlayers
        int states = 6;
        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) Arrays.fill(res[i], -1);

        int start = 0, afterLead = 1, Astate = 2, midPad = 3, Bstate = 4, accept = 5;

        // from start: any not in As (including 0) -> afterLead
        for (int x = 0; x <= nPlayers; x++) {
            if (!As.contains(x)) res[start][x] = afterLead;
        }

        // afterLead: must see any A in As to begin A+
        for (int x = 0; x <= nPlayers; x++) {
            if (As.contains(x)) res[afterLead][x] = Astate;
        }

        // Astate: A -> stay in Astate; B -> go to Bstate; any not in As or Bs -> midPad
        for (int x = 0; x <= nPlayers; x++) {
            if (As.contains(x))      res[Astate][x] = Astate;
            else if (Bs.contains(x)) res[Astate][x] = Bstate;
            else                     res[Astate][x] = midPad; // includes 0
        }

        // midPad: loop on not in As or Bs; on any B -> Bstate; any A is invalid here
        for (int x = 0; x <= nPlayers; x++) {
            if (Bs.contains(x))                        res[midPad][x] = Bstate;
            else if (!As.contains(x) && !Bs.contains(x)) res[midPad][x] = midPad;
        }

        // Bstate: any B -> stay; not in Bs -> accept
        for (int x = 0; x <= nPlayers; x++) {
            if (Bs.contains(x)) res[Bstate][x] = Bstate;
            else                res[Bstate][x] = accept;
        }

        return new Automaton(start, List.of(accept), res);
    }

    public static Automaton PAD_APLUS_PADPLUS_BPLUS_PAD(int nPlayers, Set<Integer> As, Set<Integer> Bs) {
        int inputs = nPlayers + 1;
        int states = 6;
        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) Arrays.fill(res[i], -1);

        int start = 0, afterLead = 1, Astate = 2, midPad = 3, Bstate = 4, accept = 5;

        // from start: any not in As (including 0) -> afterLead
        for (int x = 0; x <= nPlayers; x++) {
            if (!As.contains(x)) res[start][x] = afterLead;
        }

        // afterLead: must see any A in As to begin A+
        for (int x = 0; x <= nPlayers; x++) {
            if (As.contains(x)) res[afterLead][x] = Astate;
        }

        // Astate: A -> stay; not-A and not-B -> midPad; B -> INVALID (-1)
        // (B is no longer a direct transition — at least one PAD is now required)
        for (int x = 0; x <= nPlayers; x++) {
            if (As.contains(x))                            res[Astate][x] = Astate;
            else if (!Bs.contains(x))                      res[Astate][x] = midPad;
            // Bs.contains(x): stays -1 — must go through midPad first
        }

        // midPad: loop on not-A not-B; on any B -> Bstate; A is invalid
        for (int x = 0; x <= nPlayers; x++) {
            if (Bs.contains(x))                              res[midPad][x] = Bstate;
            else if (!As.contains(x) && !Bs.contains(x))    res[midPad][x] = midPad;
        }

        // Bstate: any B -> stay; not-B -> accept
        for (int x = 0; x <= nPlayers; x++) {
            if (Bs.contains(x)) res[Bstate][x] = Bstate;
            else                res[Bstate][x] = accept;
        }

        return new Automaton(start, List.of(accept), res);
    }

    //extended pass, one single passer and one single receiver
    public static Automaton PAD_APLUS_0PLUS_BPLUS_PAD(int nPlayers, Set<Integer> As, Set<Integer> Bs) {
        for (int x : As) {
            if (x < 1 || x > nPlayers) throw new IllegalArgumentException("As contains out-of-range value: " + x);
        }
        for (int x : Bs) {
            if (x < 1 || x > nPlayers) throw new IllegalArgumentException("Bs contains out-of-range value: " + x);
        }

        int inputs = nPlayers + 1;
        // States:
        //   0                          → start (the leading pad)
        //   1                          → afterLead, waiting for the passer
        //   2+a                        → A+ : passer 'a' holds the ball
        //   2+nPlayers+a               → 0+ : ball in flight, passer was 'a'
        //   2+2*nPlayers+b             → B+ : receiver 'b' holds the ball
        //   3*nPlayers+3               → accept (the trailing pad)
        // Unlike PAD_APLUS_PADPLUS_BPLUS_PAD the states remember *which* player, so
        // the A+ and B+ blocks cannot absorb a run of several different players, and
        // the middle block is strictly ball-free instead of "any symbol outside As ∪ Bs".
        int states = 3 * nPlayers + 4;
        int start = 0, afterLead = 1, accept = states - 1;

        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) Arrays.fill(res[i], -1);

        // from start: any not in As (including 0) -> afterLead
        for (int x = 0; x <= nPlayers; x++) {
            if (!As.contains(x)) res[start][x] = afterLead;
        }

        // afterLead: the passer takes the ball
        for (int a : As) {
            res[afterLead][a] = 2 + a;
        }

        // A+ : only the same passer keeps the ball; 0 opens the flight
        // (any other player stays -1: the ball changed hands, this is not that pass)
        for (int a : As) {
            res[2 + a][a] = 2 + a;
            res[2 + a][0] = 2 + nPlayers + a;
        }

        // 0+ : ball-free frames loop; a receiver != a ends the flight
        for (int a : As) {
            res[2 + nPlayers + a][0] = 2 + nPlayers + a;
            for (int b : Bs) {
                if (b != a) res[2 + nPlayers + a][b] = 2 + 2 * nPlayers + b;
            }
        }

        // B+ : only the same receiver keeps the ball; anything else is the trailing pad
        for (int b : Bs) {
            for (int x = 0; x <= nPlayers; x++) {
                res[2 + 2 * nPlayers + b][x] = (x == b) ? 2 + 2 * nPlayers + b : accept;
            }
        }

        return new Automaton(start, List.of(accept), res);
    }

    //minimal pass excluding direct change of possession
    public static Automaton A_0PLUS_B(int nPlayers) {
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

        return new Automaton(0, List.of(accept), res);
    }

    //extended pass
    public static Automaton APLUS_0STAR_BPLUS(int nPlayers) {
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

        List<Integer> acceptingStates = new ArrayList<>();
        for (int a = 1; a <= nPlayers; a++) {
            acceptingStates.add(2 * nPlayers + a);
        }

        return new Automaton(0, acceptingStates, res);
    }

    //extended pass
    public static Automaton APLUS_0STAR_BPLUS(int nPlayers, Set<Integer> As, Set<Integer> Bs) {
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
            if (As.contains(a)) {
                res[start][a] = a;
            }
        }
        // can repeat As (loop) or go to 0 phase or go to B
        for (int a = 1; a <= nPlayers; a++) {
            if (!As.contains(a)) {
                continue;
            }
            res[a][a] = a; // more As
            res[a][0] = nPlayers + a; // go to 0* phase

            for (int b = 1; b <= nPlayers; b++) {
                if (b != a && Bs.contains(b)) {
                    res[a][b] = 2 * nPlayers + a; // directly to B+ (no zeros case)
                }
            }
        }

        // 0s can repeat (loop) or go to B
        for (int a = 1; a <= nPlayers; a++) {
            if (!As.contains(a)) continue;
            res[nPlayers + a][0] = nPlayers + a;  // stay on zeros

            for (int b = 1; b <= nPlayers; b++) {
                if (b != a && Bs.contains(b)) {
                    res[nPlayers + a][b] = 2 * nPlayers + a; // start B+
                }
            }
        }

        // can repeat Bs (loop) or go to accept, but cannot go back to A or 0
        for (int a = 1; a <= nPlayers; a++) {
            if (!As.contains(a)) continue;
            for (int b = 1; b <= nPlayers; b++) {
                if (b != a && Bs.contains(b)) {
                    res[2 * nPlayers + a][b] = 2 * nPlayers + a;   // keep reading same B
                }
            }
        }

        List<Integer> acceptingStates = new ArrayList<>();
        for (int a = 1; a <= nPlayers; a++) {
            if (As.contains(a)) {
                acceptingStates.add(2 * nPlayers + a);
            }
        }

        return new Automaton(0, acceptingStates, res);
    }

    //extended pass excluding direct change of possession
    public static Automaton APLUS_0PLUS_BPLUS(int nPlayers) {
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

        List<Integer> acceptingStates = new ArrayList<>();
        for (int a = 1; a <= nPlayers; a++) {
            acceptingStates.add(2 * nPlayers + a);
        }

        return new Automaton(0, acceptingStates, res);
    }

    public static Automaton APLUS(int nPlayers, Set<Integer> As) {
        //n is the number of possible inputs excluding 0 (i.e. the number of players)
        // ACCEPTING STATES ARE 1, 2, ..., nPlayers (one for each player)

        int inputs = nPlayers + 1;
        int states = nPlayers + 1;
        int[][] res = new int[states][inputs];

        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        int start = 0;

        // from start: only non-zero symbols can begin
        for (int a = 1; a <= nPlayers; a++) {
            if (As.contains(a)) {
                res[start][a] = a;
                res[a][a] = a; // can repeat A
            }
        }

        List<Integer> acceptingStates = new ArrayList<>();
        for (int a = 1; a <= nPlayers; a++) {
            if (As.contains(a)) {
                acceptingStates.add(a);
            }
        }

        return new Automaton(0, acceptingStates, res);
    }

    public static Automaton NOTA_APLUS_NOTA(int nPlayers, Set<Integer> As) {

        int inputs = nPlayers + 1;
        int states = 4;
        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) Arrays.fill(res[i], -1);

        int start = 0, preA = 1, Astate = 2, post = 3;

        // start: exactly one notA -> preA
        for (int x = 0; x <= nPlayers; x++) {
            if (!As.contains(x)) res[start][x] = preA;
        }

        // preA: first A of the A+ block -> Astate
        for (int x = 0; x <= nPlayers; x++) {
            if (As.contains(x)) res[preA][x] = Astate;
        }

        // Astate: any A -> stay; exactly one notA -> post (accept)
        for (int x = 0; x <= nPlayers; x++) {
            if (As.contains(x))  res[Astate][x] = Astate;
            else                 res[Astate][x] = post;
        }

        // post: accept, no further transitions

        return new Automaton(start, List.of(post), res);
    }

    public static Automaton AS_PLUS(int nPlayers, Set<Integer> As) {

        int inputs = nPlayers + 1;
        int states = 2; // 0 = start, 1 = inside As+
        int[][] res = new int[states][inputs];

        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        int start = 0;
        int accept = 1;

        // from start: any valid A moves to state 1
        for (int a : As) {
            res[start][a] = accept;
        }

        // state 1: loop on any valid A
        for (int a : As) {
            res[accept][a] = accept;
        }

        return new Automaton(start, List.of(accept), res);
    }

    public static Automaton AS_WITH_INTERNAL_ZEROS(int nPlayers, Set<Integer> As) {

        int inputs = nPlayers + 1;
        int states = 3;
        int[][] res = new int[states][inputs];

        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        int start = 0;

        for (int a : As) {
            res[start][a] = 1;
        }

        for (int a : As) {
            res[1][a] = 1;
        }
        res[1][0] = 2;


        res[2][0] = 2;

        for (int a : As) {
            res[2][a] = 1; // zero must be followed by As
        }

        return new Automaton(start, List.of(1), res);
    }

    public static int[] pad(int[] input) {
        int[] padded = new int[input.length + 2];
        padded[0] = 0;
        System.arraycopy(input, 0, padded, 1, input.length);
        padded[padded.length - 1] = 0;
        return padded;
    }

    public static int[] pad(int[] input, int sentinel) {
        int[] padded = new int[input.length + 2];
        padded[0] = sentinel;
        System.arraycopy(input, 0, padded, 1, input.length);
        padded[padded.length - 1] = sentinel;
        return padded;
    }
}
