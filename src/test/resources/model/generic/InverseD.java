package com.itangcent.model.generic;

import com.itangcent.model.generic.InverseA;

/**
 * D<P, Q> extends A<Q, P> — also inverts, but with different param names.
 *
 * A's T = Q, A's R = P
 *
 * @param <P> maps to parent's R
 * @param <Q> maps to parent's T
 */
public class InverseD<P, Q> extends InverseA<Q, P> {

    /**
     * field of type P
     */
    private P p;

    /**
     * field of type Q
     */
    private Q q;

    public P getP() {
        return p;
    }

    public void setP(P p) {
        this.p = p;
    }

    public Q getQ() {
        return q;
    }

    public void setQ(Q q) {
        this.q = q;
    }
}
