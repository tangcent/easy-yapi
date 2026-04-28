package com.itangcent.model.generic;

/**
 * Generic pair with two type parameters.
 *
 * @param <A> first type
 * @param <B> second type
 */
public class Pair<A, B> {

    /**
     * first element
     */
    private A first;

    /**
     * second element
     */
    private B second;

    public A getFirst() {
        return first;
    }

    public void setFirst(A first) {
        this.first = first;
    }

    public B getSecond() {
        return second;
    }

    public void setSecond(B second) {
        this.second = second;
    }
}
