package com.itangcent.model.generic;

import com.itangcent.model.generic.InverseA;

/**
 * B<X, Y> extends A<Y, X> — inverts the type parameters.
 *
 * A's T = Y, A's R = X
 *
 * @param <X> maps to parent's R
 * @param <Y> maps to parent's T
 */
public class InverseB<X, Y> extends InverseA<Y, X> {

    /**
     * field of type X
     */
    private X x;

    /**
     * field of type Y
     */
    private Y y;

    public X getX() {
        return x;
    }

    public void setX(X x) {
        this.x = x;
    }

    public Y getY() {
        return y;
    }

    public void setY(Y y) {
        this.y = y;
    }
}
