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

    //inverse
    public static Automaton NOT_contains_A_0STAR_B(int nPlayers, Set<Integer> As, Set<Integer> Bs) {
        // inputs: 0..nPlayers (0 is zero-token), states as described in explanation
        for (int x : As) {
            if (x < 1 || x > nPlayers) throw new IllegalArgumentException("As contains out-of-range value: " + x);
        }
        for (int x : Bs) {
            if (x < 1 || x > nPlayers) throw new IllegalArgumentException("Bs contains out-of-range value: " + x);
        }

        int inputs = nPlayers + 1;
        int start = 0;
        int seen = nPlayers + 1;      // we have consumed >=1 token and are not inside an A...0* run
        int found = nPlayers + 2;     // trap state: forbidden substring was observed
        int states = nPlayers + 3;

        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) {
            Arrays.fill(res[i], -1);
        }

        // --- transitions from start (no token consumed yet) ---
        // on 0 -> seen (consumed a token, no A started)
        res[start][0] = seen;
        // on non-zero x:
        for (int x = 1; x <= nPlayers; x++) {
            if (As.contains(x)) {
                // start potential A = x
                res[start][x] = x;        // state x means "we saw A = x (and possibly zeros later)"
            } else {
                res[start][x] = seen;     // generic seen state
            }
        }

        // --- transitions for the "seen" state (we have consumed >=1, no active A-run) ---
        res[seen][0] = seen; // zeros keep us in seen
        for (int x = 1; x <= nPlayers; x++) {
            if (As.contains(x)) {
                res[seen][x] = x;    // a new A starts
            } else {
                res[seen][x] = seen; // stay in seen
            }
        }

        // --- transitions for states a = 1..nPlayers (these are "we saw A=a and maybe zeros") ---
        for (int a = 1; a <= nPlayers; a++) {
            // if 'a' is not actually an A, this state will be unreachable but harmless.
            // on 0 -> remain in the same a-state (A 0* ...)
            res[a][0] = a;

            for (int x = 1; x <= nPlayers; x++) {
                // If x is a B and x != a -> forbidden pattern A ... B found => go to trap
                if (Bs.contains(x) && x != a) {
                    res[a][x] = found;
                } else if (As.contains(x)) {
                    // start a new A = x (note: B-check took precedence above)
                    res[a][x] = x;
                } else {
                    // neither B (that would complete) nor an A -> fall back to 'seen'
                    res[a][x] = seen;
                }
            }
        }

        // --- trap state loops to itself on any input ---
        for (int x = 0; x < inputs; x++) res[found][x] = found;

        // --- accepting states: all states except start and found (ensures empty string rejected) ---
        List<Integer> accepting = new ArrayList<>();
        for (int i = 1; i < found; i++) accepting.add(i); // 1 .. nPlayers and 'seen'

        return new Automaton(start, accepting, res);
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

        int inputs = nPlayers + 1; // symbols 0..nPlayers
        int states = nPlayers + 3;
        int[][] res = new int[states][inputs];
        for (int i = 0; i < states; i++) Arrays.fill(res[i], -1);

        int start = 0;
        int preA = nPlayers + 1;
        int post = nPlayers + 2;

        // from start: any notA (including 0) → preA
        for (int x = 0; x <= nPlayers; x++) {
            if (!As.contains(x)) {
                res[start][x] = preA;
            }
        }

        // from preA: first A of the A+ block
        for (int a : As) {
            if (a >= 1 && a <= nPlayers) {
                res[preA][a] = a;
            }
        }

        // A+ part
        for (int a : As) {
            if (a >= 1 && a <= nPlayers) {

                // Allow transitioning to any valid A (including itself)
                for (int otherA : As) {
                    if (otherA >= 1 && otherA <= nPlayers) {
                        res[a][otherA] = otherA;
                    }
                }

                // Any non-A symbol ends the A+ block
                for (int x = 0; x <= nPlayers; x++) {
                    if (!As.contains(x)) {
                        res[a][x] = post; // trailing notA (including 0)
                    }
                }
            }
        }

        List<Integer> acceptingStates = new ArrayList<>();
        acceptingStates.add(post);

        return new Automaton(start, acceptingStates, res);
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
}
