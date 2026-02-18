package org.main.util;

import java.util.*;

public class Automaton {

    int initState;
    List<Integer> acceptingStates;
    int[][] automaton;
    int[] symbolSequence;

    public Automaton(int initState, List<Integer> acceptingStates, int[][] automaton) {
        this.initState = initState;
        this.acceptingStates = acceptingStates;
        this.automaton = automaton;
    }

    public Automaton(int initState, List<Integer> acceptingStates, int[][] automaton, int[] symbolSequence) {
        this.initState = initState;
        this.acceptingStates = acceptingStates;
        this.automaton = automaton;
        this.symbolSequence = symbolSequence;
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

    public int[] getSymbolSequence() { return symbolSequence; }

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

        return new Automaton(0, Arrays.asList(accept), res);
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

        return new Automaton(0, Arrays.asList(accept), res);
    }

    //minimal movement
    public static final Automaton A_NOTB_STAR_B(int nPlayers, int A, int B) {
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
            } else if (i != A || i != 0) { //loop
                res[1][i] = 1;
            }
        }

        return new Automaton(0, Arrays.asList(accept), res);
    }

    //extended movement
    public static final Automaton APLUS_NOTB_STAR_BPLUS(int nPlayers, int A, int B) {
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

        return new Automaton(0, Arrays.asList(accept), res);
    }

    //minimal pass excluding direct change of possession
    public static final Automaton A_0PLUS_B(int nPlayers) {
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

        return new Automaton(0, Arrays.asList(accept), res);
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

        return new Automaton(start, Arrays.asList(accept), res);
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

        return new Automaton(start, Arrays.asList(1), res);
    }


    /**
     * Builds a DFA recognizing possession sequences where every non-zero token
     * belongs to "the team," defined by:
     * <p>
     * - fixedMembers:  player IDs always considered part of the team (no binding needed)
     * - boundSlots:    variable slots, each with a constraint set of eligible player IDs.
     * An EMPTY constraint set means the slot is fully anonymous (any player).
     * Once a slot is committed to a player, it stays committed.
     * <p>
     * Uses NFA-to-DFA subset construction over partial-binding states.
     * All reachable DFA states are accepting (rejection = seeing a non-team player).
     *
     * @param nPlayers     total number of players (IDs 1..nPlayers), 0 = "no one"
     * @param fixedMembers always-in-team player IDs
     * @param boundSlots   variable slots; each Set<Integer> is the constraint
     *                     (empty = anonymous, i.e. any player 1..nPlayers)
     */
    /**
     * Finds all maximal intervals where the team has possession,
     * using direct NFA simulation with binding states.
     * Returns list of [start, end] pairs (inclusive, in original array indices).
     */
    public static List<int[]> findTeamPossessionWindows(
            int[] possession,
            int nPlayers,
            Set<Integer> fixedMembers,
            List<Set<Integer>> boundSlots) {

        List<int[]> windows = new ArrayList<>();
        int n = possession.length;
        int i = 0;

        while (i < n) {
            // Skip zeros and non-team openers
            if (possession[i] == 0) { i++; continue; }

            // Try to start a window at i
            // Run NFA forward greedily, tracking all valid binding worlds
            Set<String> currentNFA = new LinkedHashSet<>();
            Map<String, int[]> cache = new HashMap<>();

            int[] initBinding = new int[boundSlots.size()];
            Arrays.fill(initBinding, -1);
            String initKey = Arrays.toString(initBinding);
            cache.put(initKey, initBinding);
            currentNFA.add(initKey);

            int windowStart = -1;
            int windowEnd = -1;
            int j = i;

            while (j < n) {
                int sym = possession[j];

                if (sym == 0) {
                    // Zero: everyone stays, no binding change
                    if (windowStart != -1) windowEnd = j; // zeros extend the window
                    j++;
                    continue;
                }

                // Compute next NFA states
                Set<String> nextNFA = new LinkedHashSet<>();
                for (String key : currentNFA) {
                    int[] binding = cache.get(key);
                    for (int[] succ : nfaStep(binding, sym, nPlayers, fixedMembers, boundSlots)) {
                        String succKey = Arrays.toString(succ);
                        cache.putIfAbsent(succKey, succ);
                        nextNFA.add(succKey);
                    }
                }

                if (nextNFA.isEmpty()) {
                    // sym is not a valid team member in any binding world → window ends
                    break;
                }

                currentNFA = nextNFA;
                if (windowStart == -1) windowStart = j;
                windowEnd = j;
                j++;
            }

            if (windowStart != -1) {
                // Trim trailing zeros from window
                while (windowEnd > windowStart && possession[windowEnd] == 0) windowEnd--;
                windows.add(new int[]{windowStart, windowEnd});
                i = windowEnd + 1; // resume after the window
            } else {
                i++;
            }
        }

        return windows;
    }

    /**
     * Pad possession array with sentinel at both ends.
     * Handles the edge case where team possession starts at index 0
     * or ends at the last index.
     */
    public static int[] padPossession(int[] possession, int nPlayers) {
        int sentinel = nPlayers + 1;
        int[] padded = new int[possession.length + 2];
        padded[0] = sentinel;
        System.arraycopy(possession, 0, padded, 1, possession.length);
        padded[padded.length - 1] = sentinel;
        return padded;
    }

    // -----------------------------------------------------------------------
// Computes the set of NFA successors for a DFA state on a given symbol,
// collecting them into the next DFA state (= set of NFA states).
// -----------------------------------------------------------------------
    private static Set<String> computeNextDFA(
            Set<String> dfaState, int sym, int nPlayers,
            Set<Integer> fixedMembers, List<Set<Integer>> boundSlots,
            Map<String, int[]> nfaCache) {

        Set<String> next = new LinkedHashSet<>();

        for (String nfaKey : dfaState) {
            int[] binding = nfaCache.get(nfaKey);

            for (int[] succ : nfaStep(binding, sym, nPlayers, fixedMembers, boundSlots)) {
                String succKey = Arrays.toString(succ);
                nfaCache.putIfAbsent(succKey, succ);
                next.add(succKey);
            }
        }

        return next;
    }

    // -----------------------------------------------------------------------
// Core NFA transition: given a binding and a symbol, returns all
// possible successor bindings (may be 0 = dead, 1 = deterministic, or
// >1 = genuinely nondeterministic due to multiple eligible unbound slots).
// -----------------------------------------------------------------------
    private static List<int[]> nfaStep(
            int[] binding, int sym, int nPlayers,
            Set<Integer> fixedMembers, List<Set<Integer>> boundSlots) {

        int k = binding.length;

        // --- Symbol 0: no possession change ---
        if (sym == 0) {
            return Collections.singletonList(Arrays.copyOf(binding, k));
        }

        // --- Fixed member: always valid, no binding change ---
        if (fixedMembers.contains(sym)) {
            return Collections.singletonList(Arrays.copyOf(binding, k));
        }

        // --- Already bound to some slot: valid, no change ---
        for (int i = 0; i < k; i++) {
            if (binding[i] == sym) {
                return Collections.singletonList(Arrays.copyOf(binding, k));
            }
        }

        // --- Try to assign sym to an eligible unbound slot (NFA branches) ---
        List<int[]> successors = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            if (binding[i] == -1) {
                Set<Integer> constraint = boundSlots.get(i);
                boolean eligible = constraint.isEmpty()  // anonymous slot
                        || constraint.contains(sym);      // or explicitly allowed
                if (eligible) {
                    int[] newBinding = Arrays.copyOf(binding, k);
                    newBinding[i] = sym;
                    successors.add(newBinding);
                }
            }
        }

        // If successors is empty -> sym is not a valid team player -> dead transition
        return successors;
    }

    public static Automaton TEAM_ZONE(
            int nPlayers,
            int nFrames,
            int[][] matrix,             // matrix[playerId][frame] = zone (1-indexed player IDs)
            Set<Integer> validZones,
            Set<Integer> fixedMembers,
            List<Set<Integer>> boundSlots,
            int minK) {

        int k = boundSlots.size();

        // ------------------------------------------------------------------
        // Step 1: Compute bitmask per frame.
        // Bit (p-1) is set iff player p is in a valid zone at that frame.
        // ------------------------------------------------------------------
        int[] frameMasks = new int[nFrames];
        for (int t = 0; t < nFrames; t++) {
            int mask = 0;
            for (int p = 1; p <= nPlayers; p++) {
                if (validZones.contains(matrix[p][t])) {
                    mask |= (1 << (p - 1));
                }
            }
            frameMasks[t] = mask;
        }

        // ------------------------------------------------------------------
        // Step 2: Compress alphabet.
        // Map each unique bitmask to a small integer symbol.
        // Sentinel mask = 0 (nobody in valid zone) always present.
        // ------------------------------------------------------------------
        List<Integer> uniqueMasks = new ArrayList<>();
        Map<Integer, Integer> maskToSym = new LinkedHashMap<>();
        maskToSym.put(0, 0);       // sentinel is always symbol 0
        uniqueMasks.add(0);

        for (int m : frameMasks) {
            if (!maskToSym.containsKey(m)) {
                maskToSym.put(m, uniqueMasks.size());
                uniqueMasks.add(m);
            }
        }

        int inputs = uniqueMasks.size();
        int sentinelSym = 0; // symbol index of mask=0

        // ------------------------------------------------------------------
        // Step 3: NFA-to-DFA subset construction (same structure as
        // TEAM_POSSESSION, but transitions use bitmask logic).
        // ------------------------------------------------------------------
        Map<String, int[]> nfaCache = new HashMap<>();
        int[] initBinding = new int[k];
        Arrays.fill(initBinding, -1);
        String initNFAKey = Arrays.toString(initBinding);
        nfaCache.put(initNFAKey, initBinding);

        Set<String> initDFA = new LinkedHashSet<>();
        initDFA.add(initNFAKey);
        String initDFAKey = canonicalDFAKey(initDFA);

        Map<String, Integer> dfaKeyToIndex = new LinkedHashMap<>();
        List<int[]> transitionRows = new ArrayList<>();
        final int ACCEPT_PLACEHOLDER = Integer.MAX_VALUE;

        dfaKeyToIndex.put(initDFAKey, 0);
        transitionRows.add(filledRow(inputs, -1));

        Queue<Set<String>> queue = new ArrayDeque<>();
        queue.add(initDFA);

        while (!queue.isEmpty()) {
            Set<String> current = queue.poll();
            String currentKey = canonicalDFAKey(current);
            int currentIdx = dfaKeyToIndex.get(currentKey);
            boolean isInitial = currentKey.equals(initDFAKey);

            for (int symIdx = 0; symIdx < inputs; symIdx++) {
                int mask = uniqueMasks.get(symIdx);
                Set<String> next = computeNextDFAZone(
                        current, mask, nPlayers, fixedMembers, boundSlots, nfaCache, minK);

                if (!next.isEmpty()) {
                    String nextKey = canonicalDFAKey(next);
                    if (!dfaKeyToIndex.containsKey(nextKey)) {
                        dfaKeyToIndex.put(nextKey, transitionRows.size());
                        transitionRows.add(filledRow(inputs, -1));
                        queue.add(next);
                    }
                    transitionRows.get(currentIdx)[symIdx] = dfaKeyToIndex.get(nextKey);
                } else if (!isInitial) {
                    // Team condition fails after possession started → ACCEPT
                    transitionRows.get(currentIdx)[symIdx] = ACCEPT_PLACEHOLDER;
                }
            }
        }

        // Add ACCEPT sink
        int acceptIdx = transitionRows.size();
        transitionRows.add(filledRow(inputs, -1));

        for (int[] row : transitionRows) {
            for (int sym = 0; sym < inputs; sym++) {
                if (row[sym] == ACCEPT_PLACEHOLDER) row[sym] = acceptIdx;
            }
        }

        // ------------------------------------------------------------------
        // Step 4: Prepend PRE state (index 0 after shift).
        // From pre-state, every symbol → old-initial (index 1 after shift).
        // Unlike TEAM_POSSESSION, we can't filter here: with wildcards any
        // mask could potentially start a valid window, so we route everything
        // to old-initial unconditionally and let the DFA decide.
        // ------------------------------------------------------------------
        int[] preRow = filledRow(inputs, -1);
        for (int sym = 0; sym < inputs; sym++) {
            preRow[sym] = 1; // everything → old-initial (shifted to 1)
        }
        // sym=0 (nobody in zone) is a no-op: stay at pre-state
        preRow[sentinelSym] = 1;

        int totalOld = transitionRows.size();
        int[][] table = new int[totalOld + 1][inputs];
        table[0] = preRow;
        for (int i = 0; i < totalOld; i++) {
            for (int sym = 0; sym < inputs; sym++) {
                int t = transitionRows.get(i)[sym];
                table[i + 1][sym] = (t == -1) ? -1 : t + 1;
            }
        }

        // ------------------------------------------------------------------
        // Step 5: Build padded symbol sequence.
        // Pad both ends with sentinel (mask=0 → symbol 0).
        // ------------------------------------------------------------------
        int[] symbolSeq = new int[nFrames + 2];
        symbolSeq[0] = sentinelSym;
        for (int t = 0; t < nFrames; t++) {
            symbolSeq[t + 1] = maskToSym.get(frameMasks[t]);
        }
        symbolSeq[nFrames + 1] = sentinelSym;

        return new Automaton(0, List.of(acceptIdx + 1), table, symbolSeq);
    }

    // ------------------------------------------------------------------
// Zone-specific NFA DFA step
// ------------------------------------------------------------------

    private static Set<String> computeNextDFAZone(
            Set<String> dfaState, int mask, int nPlayers,
            Set<Integer> fixedMembers, List<Set<Integer>> boundSlots,
            Map<String, int[]> nfaCache, int minK) {   // <-- new parameter

        Set<String> next = new LinkedHashSet<>();
        for (String nfaKey : dfaState) {
            int[] binding = nfaCache.get(nfaKey);
            for (int[] succ : nfaStepZone(binding, mask, nPlayers,
                    fixedMembers, boundSlots, minK)) {
                String succKey = Arrays.toString(succ);
                nfaCache.putIfAbsent(succKey, succ);
                next.add(succKey);
            }
        }
        return next;
    }

    private static List<int[]> nfaStepZone(
            int[] binding, int mask, int nPlayers,
            Set<Integer> fixedMembers, List<Set<Integer>> boundSlots, int minK) {

        int k = binding.length;

        // Count fixed members currently in valid zones
        int baseCount = 0;
        for (int p : fixedMembers) {
            if (((mask >> (p - 1)) & 1) == 1) baseCount++;
        }

        // Count already-bound slots whose player is currently in valid zones
        for (int i = 0; i < k; i++) {
            if (binding[i] != -1 && ((mask >> (binding[i] - 1)) & 1) == 1) {
                baseCount++;
            }
        }

        // Enumerate all completions for unbound slots, passing the running count
        List<int[]> result = new ArrayList<>();
        enumerateZoneBindings(Arrays.copyOf(binding, k), mask, boundSlots,
                nPlayers, fixedMembers, 0, baseCount, minK, result);
        return result;
    }

    private static void enumerateZoneBindings(
            int[] binding, int mask, List<Set<Integer>> boundSlots,
            int nPlayers, Set<Integer> fixedMembers,
            int slotIdx, int currentCount, int minK, List<int[]> result) {

        // Advance to next unbound slot
        while (slotIdx < binding.length && binding[slotIdx] != -1) slotIdx++;

        if (slotIdx == binding.length) {
            // All slots resolved: check threshold
            if (currentCount >= minK) {
                result.add(Arrays.copyOf(binding, binding.length));
            }
            return;
        }

        // Pruning: count remaining unbound slots
        int remainingSlots = 0;
        for (int i = slotIdx; i < binding.length; i++) {
            if (binding[i] == -1) remainingSlots++;
        }
        // Even if all remaining slots are in zone, can we ever reach minK?
        if (currentCount + remainingSlots < minK) return;

        Set<Integer> constraint = boundSlots.get(slotIdx);

        for (int p = 1; p <= nPlayers; p++) {
            // Skip fixed members — they're already counted separately
            if (fixedMembers.contains(p)) continue;
            // Must satisfy slot constraint (empty = anonymous)
            if (!constraint.isEmpty() && !constraint.contains(p)) continue;
            // Must not already be bound to another slot
            boolean taken = false;
            for (int j = 0; j < binding.length; j++) {
                if (binding[j] == p) { taken = true; break; }
            }
            if (taken) continue;

            boolean inZone = ((mask >> (p - 1)) & 1) == 1;
            binding[slotIdx] = p;
            enumerateZoneBindings(binding, mask, boundSlots, nPlayers, fixedMembers,
                    slotIdx + 1, currentCount + (inZone ? 1 : 0), minK, result);
            binding[slotIdx] = -1;
        }
    }

    public static Automaton TEAM_MOVE_TO(
            int nPlayers,
            int nFrames,
            int[][] matrix,
            int zoneStart,
            int zoneEnd,
            Set<Integer> fixedMembers,
            List<Set<Integer>> boundSlots,
            int minK) {

        int k = boundSlots.size();

        // ------------------------------------------------------------------
        // Step 1: Compute per-frame (startMask, endMask) pairs.
        // startMask: players in zoneStart. endMask: players in zoneEnd.
        // ------------------------------------------------------------------
        int[] startMasks = new int[nFrames];
        int[] endMasks   = new int[nFrames];
        for (int t = 0; t < nFrames; t++) {
            for (int p = 1; p <= nPlayers; p++) {
                if (matrix[p][t] == zoneStart) startMasks[t] |= (1 << (p - 1));
                if (matrix[p][t] == zoneEnd)   endMasks[t]   |= (1 << (p - 1));
            }
        }

        // ------------------------------------------------------------------
        // Step 2: Compress alphabet.
        // Sentinel (0,0) = nobody relevant, always symbol 0.
        // ------------------------------------------------------------------
        Map<Long, Integer> pairToSym = new LinkedHashMap<>();
        List<int[]> uniquePairs = new ArrayList<>();

        pairToSym.put(pairKey(0, 0), 0);
        uniquePairs.add(new int[]{0, 0});
        int sentinelSym = 0;

        int[] frameSym = new int[nFrames];
        for (int t = 0; t < nFrames; t++) {
            long key = pairKey(startMasks[t], endMasks[t]);
            if (!pairToSym.containsKey(key)) {
                pairToSym.put(key, uniquePairs.size());
                uniquePairs.add(new int[]{startMasks[t], endMasks[t]});
            }
            frameSym[t] = pairToSym.get(key);
        }

        int inputs = uniquePairs.size();

        // ------------------------------------------------------------------
        // Step 3: NFA-to-DFA subset construction.
        //
        // NFA state key: phase + ":" + Arrays.toString(binding) + "|" + activeMask
        //   phase 0 = waiting for zone_start frame  (activeMask unused, always 0)
        //   phase 1 = in motion                     (activeMask = committed participants)
        // ------------------------------------------------------------------
        Map<String, int[]>   nfaBinding    = new HashMap<>();
        Map<String, Integer> nfaPhase      = new HashMap<>();
        Map<String, Integer> nfaActiveMask = new HashMap<>();

        int[] initBinding = new int[k];
        Arrays.fill(initBinding, -1);
        String initNFAKey = moveNfaKey(0, initBinding, 0);
        nfaBinding.put(initNFAKey, initBinding);
        nfaPhase.put(initNFAKey, 0);
        nfaActiveMask.put(initNFAKey, 0);

        Set<String> initDFA = new LinkedHashSet<>();
        initDFA.add(initNFAKey);
        String initDFAKey = canonicalDFAKey(initDFA);

        Map<String, Integer> dfaKeyToIndex = new LinkedHashMap<>();
        List<int[]> transitionRows = new ArrayList<>();
        final int ACCEPT_PLACEHOLDER = Integer.MAX_VALUE;

        dfaKeyToIndex.put(initDFAKey, 0);
        transitionRows.add(filledRow(inputs, -1));

        Queue<Set<String>> queue = new ArrayDeque<>();
        queue.add(initDFA);

        while (!queue.isEmpty()) {
            Set<String> current = queue.poll();
            String currentKey = canonicalDFAKey(current);
            int currentIdx = dfaKeyToIndex.get(currentKey);
            boolean isInitial = currentKey.equals(initDFAKey);

            for (int symIdx = 0; symIdx < inputs; symIdx++) {
                int[] pair      = uniquePairs.get(symIdx);
                int startMask   = pair[0];
                int endMask     = pair[1];

                Set<String> next = new LinkedHashSet<>();
                boolean hasAccept = false;

                for (String nfaKey : current) {
                    int[] binding  = nfaBinding.get(nfaKey);
                    int   phase    = nfaPhase.get(nfaKey);
                    int   active   = nfaActiveMask.get(nfaKey);

                    if (phase == 0) {
                        // Try all binding completions where ≥minK players are in zoneStart.
                        // Each completion produces a phase-1 state with a fixed activeMask.
                        List<Object[]> completions = new ArrayList<>();
                        int baseCount      = 0;
                        int baseActiveMask = 0;
                        for (int p : fixedMembers) {
                            if (((startMask >> (p - 1)) & 1) == 1) {
                                baseCount++;
                                baseActiveMask |= (1 << (p - 1));
                            }
                        }
                        for (int i = 0; i < k; i++) {
                            if (binding[i] != -1 && ((startMask >> (binding[i] - 1)) & 1) == 1) {
                                baseCount++;
                                baseActiveMask |= (1 << (binding[i] - 1));
                            }
                        }
                        enumerateMoveStartBindings(
                                Arrays.copyOf(binding, k), startMask, boundSlots,
                                nPlayers, fixedMembers, minK, 0,
                                baseCount, baseActiveMask, completions);

                        for (Object[] comp : completions) {
                            int[] newBinding   = (int[]) comp[0];
                            int   newActiveMask = (int)  comp[1];

                            // Single-frame move (zoneStart == zoneEnd): accept immediately
                            if ((newActiveMask & endMask) == newActiveMask) {
                                hasAccept = true;
                            } else {
                                String newKey = moveNfaKey(1, newBinding, newActiveMask);
                                nfaBinding.putIfAbsent(newKey, newBinding);
                                nfaPhase.putIfAbsent(newKey, 1);
                                nfaActiveMask.putIfAbsent(newKey, newActiveMask);
                                next.add(newKey);
                            }
                        }

                    } else { // phase == 1
                        if ((active & endMask) == active) {
                            // All participating players reached zoneEnd → accept
                            hasAccept = true;
                            // Do NOT continue: interval ends here
                        } else {
                            // Middle frame: stay in this phase-1 state
                            next.add(nfaKey);
                        }
                    }
                }

                if (hasAccept && !isInitial) {
                    transitionRows.get(currentIdx)[symIdx] = ACCEPT_PLACEHOLDER;
                } else if (!next.isEmpty()) {
                    String nextKey = canonicalDFAKey(next);
                    if (!dfaKeyToIndex.containsKey(nextKey)) {
                        dfaKeyToIndex.put(nextKey, transitionRows.size());
                        transitionRows.add(filledRow(inputs, -1));
                        queue.add(next);
                    }
                    transitionRows.get(currentIdx)[symIdx] = dfaKeyToIndex.get(nextKey);
                }
            }

            // Sentinel from non-initial → ACCEPT (handles end-of-sequence padding)
            if (!isInitial) {
                transitionRows.get(currentIdx)[sentinelSym] = ACCEPT_PLACEHOLDER;
            }
        }

        // ACCEPT sink
        int acceptIdx = transitionRows.size();
        transitionRows.add(filledRow(inputs, -1));
        for (int[] row : transitionRows)
            for (int s = 0; s < inputs; s++)
                if (row[s] == ACCEPT_PLACEHOLDER) row[s] = acceptIdx;

        // PRE state: any symbol → old-initial (index 1 after shift)
        int[] preRow = filledRow(inputs, -1);
        for (int sym = 0; sym < inputs; sym++) preRow[sym] = 1;

        int totalOld = transitionRows.size();
        int[][] table = new int[totalOld + 1][inputs];
        table[0] = preRow;
        for (int i = 0; i < totalOld; i++)
            for (int sym = 0; sym < inputs; sym++) {
                int t = transitionRows.get(i)[sym];
                table[i + 1][sym] = (t == -1) ? -1 : t + 1;
            }



        // Padded symbol sequence
        int[] symbolSeq = new int[nFrames + 2];
        symbolSeq[0] = sentinelSym;
        for (int t = 0; t < nFrames; t++) symbolSeq[t + 1] = frameSym[t];
        symbolSeq[nFrames + 1] = sentinelSym;

        return new Automaton(0, List.of(acceptIdx + 1), table, symbolSeq);
    }

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

    private static long pairKey(int startMask, int endMask) {
        return ((long) startMask << 32) | (endMask & 0xFFFFFFFFL);
    }

    private static String moveNfaKey(int phase, int[] binding, int activeMask) {
        return phase + ":" + Arrays.toString(binding) + "|" + activeMask;
    }

    /**
     * Backtracking enumeration of binding completions + activeMask choices
     * for phase-0 → phase-1 transition. Only produces completions where
     * the total count of participants (fixed + bound) in zone_start >= minK.
     * Unlike enumerateZoneBindings, players do NOT need to be in zone_start
     * to be bound — they just don't count toward minK if they aren't.
     */
    private static void enumerateMoveStartBindings(
            int[] binding, int startMask, List<Set<Integer>> boundSlots,
            int nPlayers, Set<Integer> fixedMembers, int minK,
            int slotIdx, int currentCount, int currentActiveMask,
            List<Object[]> results) {

        // Advance to next unbound slot
        while (slotIdx < binding.length && binding[slotIdx] != -1) slotIdx++;

        if (slotIdx == binding.length) {
            if (currentCount >= minK) {
                results.add(new Object[]{Arrays.copyOf(binding, binding.length), currentActiveMask});
            }
            return;
        }

        // Pruning: remaining unbound slots can each contribute at most 1
        int remainingSlots = 0;
        for (int i = slotIdx; i < binding.length; i++)
            if (binding[i] == -1) remainingSlots++;
        if (currentCount + remainingSlots < minK) return;

        Set<Integer> constraint = boundSlots.get(slotIdx);

        for (int p = 1; p <= nPlayers; p++) {
            if (fixedMembers.contains(p)) continue;
            if (!constraint.isEmpty() && !constraint.contains(p)) continue;
            boolean taken = false;
            for (int j = 0; j < binding.length; j++)
                if (binding[j] == p) { taken = true; break; }
            if (taken) continue;

            boolean inStart = ((startMask >> (p - 1)) & 1) == 1;
            binding[slotIdx] = p;
            enumerateMoveStartBindings(
                    binding, startMask, boundSlots, nPlayers, fixedMembers, minK,
                    slotIdx + 1,
                    currentCount + (inStart ? 1 : 0),
                    currentActiveMask | (inStart ? (1 << (p - 1)) : 0),
                    results);
            binding[slotIdx] = -1;
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private static String canonicalDFAKey(Set<String> dfaState) {
        List<String> sorted = new ArrayList<>(dfaState);
        Collections.sort(sorted);
        return sorted.toString();
    }

    private static int[] filledRow(int size, int value) {
        int[] row = new int[size];
        Arrays.fill(row, value);
        return row;
    }


}
