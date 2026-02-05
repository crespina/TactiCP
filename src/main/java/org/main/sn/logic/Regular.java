package org.main.sn.logic;

/*
 * mini-cp is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License  v3
 * as published by the Free Software Foundation.
 *
 * mini-cp is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY.
 * See the GNU Lesser General Public License  for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with mini-cp. If not, see http://www.gnu.org/licenses/lgpl-3.0.en.html
 *
 * Copyright (c)  2018. by Laurent Michel, Pierre Schaus, Pascal Van Hentenryck
 *
 * mini-cpbp, replacing classic propagation by belief propagation
 * Copyright (c)  2019. by Gilles Pesant
 */

import org.maxicp.cp.engine.core.AbstractCPConstraint;
import org.maxicp.cp.engine.core.CPIntVar;

import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Regular Constraint
 */
public class Regular extends AbstractCPConstraint {
    private CPIntVar[] x;
    private int[][] transitionFct;
    private int initialState;
    private List<Integer> finalStates;
    private int n;
    private int nbStates;
    private double[][] ip; // ip[i][j]>0 for state j reached by reading x[0]..x[i-1] from the initial state
    private double[][] op; // op[i][j]>0 for state j reaching a final state by reading x[i+1]..x[n-1]
    private int[][] domArray;

    /**
     * Creates a regular constraint.
     * <p> This constraint holds iff
     * {@code x is a word recognized by the automaton}.
     *
     * @param x an array of variables
     * @param A a 2D array giving the transition function of the automaton: {states} x {domain values} -> {states} (domain values are nonnegative and start at 0)
     * @param s is the initial state
     * @param f a list of accepting states
     *          <p>
     *          Note: any negative value in A indicates that there is no valid transition from the given state on that given domain value
     */
    public Regular(CPIntVar[] x, int[][] A, int s, List<Integer> f) {
        super(x[0].getSolver());
        this.x = x;
        n = x.length;
        nbStates = A.length;
        initialState = s;
        assert ((initialState >= 0) && (initialState < nbStates));
        finalStates = new ArrayList<Integer>();
        Iterator<Integer> itr = f.iterator();
        while (itr.hasNext()) {
            int state = itr.next();
            assert ((state >= 0) && (state < nbStates));
            finalStates.add(state);
        }
        int maxVal = Integer.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            if (x[i].max() > maxVal)
                maxVal = x[i].max();
        }
        transitionFct = new int[nbStates][maxVal + 1];
        for (int i = 0; i < nbStates; i++) {
            assert (A[i].length == maxVal + 1);
            for (int j = 0; j < maxVal + 1; j++) {
                assert (A[i][j] < nbStates);
                transitionFct[i][j] = A[i][j];
            }
        }

        ip = new double[n][nbStates];
        op = new double[n][nbStates];
        domArray = new int[x.length][];
    }

    @Override
    public void post() {
        for (CPIntVar var : x) var.propagateOnDomainChange(this);
        propagate();
    }

    @Override
    public void propagate() {
        for (int i = 0; i < n; i++) {
            Arrays.fill(ip[i], 0);
        }
        // Reach forward
        ip[0][initialState] = 1;
        for (int i = 0; i < n - 1; i++) {
            domArray[i] = new int[x[i].size()];
            int s = x[i].fillArray(domArray[i]);
            for (int j = 0; j < s; j++) {
                int v = domArray[i][j];
                for (int k = 0; k < nbStates; k++) {
                    int newState = transitionFct[k][v];
                    if ((newState >= 0) && (ip[i][k] > 0)) {
                        ip[i + 1][newState] = 1;
                    }
                }
            }
        }

        for (int i = 0; i < n; i++) {
            Arrays.fill(op[i], 0);
        }
        // Reach backward and remove unsupported var/val pairs
        for (Integer finalState : finalStates) {
            int tmp = finalState;
            op[n - 1][tmp] = 1;
        }
        for (int i = n - 1; i > 0; i--) {
            int s = x[i].fillArray(domArray[i]);
            for (int j = 0; j < s; j++) {
                int v = domArray[i][j];
                double belief = 0;
                for (int k = 0; k < nbStates; k++) {
                    int newState = transitionFct[k][v];
                    if ((newState >= 0) && (op[i][newState] > 0)) {
                        op[i - 1][k] = 1;
                        if (ip[i][k] > 0) {
                            belief = 1;
                        }
                    }
                }
                if (belief == 0)
                    x[i].remove(v);
            }
        }
        int s = x[0].fillArray(domArray[0]);
        for (int j = 0; j < s; j++) {
            int v = domArray[0][j];
            int newState = transitionFct[initialState][v];
            if ((newState < 0) || (op[0][newState] == 0)) {
                x[0].remove(v);
            }
        }
    }
}
