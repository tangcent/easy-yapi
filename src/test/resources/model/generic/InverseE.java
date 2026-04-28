package com.itangcent.model.generic;

import com.itangcent.model.generic.InverseD;

/**
 * E<M, N> extends D<N, M> — inverts AGAIN on top of D's inversion.
 *
 * D<P=N, Q=M> extends A<Q, P> = A<M, N>
 *
 * So double-inversion cancels out:
 *   E<M, N> → D<N, M> → A<M, N>
 *   A's T = M, A's R = N
 *
 * @param <M> ends up as A's T (after double inversion)
 * @param <N> ends up as A's R (after double inversion)
 */
public class InverseE<M, N> extends InverseD<N, M> {

    /**
     * field of type M
     */
    private M m;

    /**
     * field of type N
     */
    private N n;

    public M getM() {
        return m;
    }

    public void setM(M m) {
        this.m = m;
    }

    public N getN() {
        return n;
    }

    public void setN(N n) {
        this.n = n;
    }
}
