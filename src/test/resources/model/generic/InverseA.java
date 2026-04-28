package com.itangcent.model.generic;

/**
 * A<T, R> { T t; R r; }
 *
 * @param <T> first type
 * @param <R> second type
 */
public class InverseA<T, R> {

    /**
     * field of type T
     */
    private T t;

    /**
     * field of type R
     */
    private R r;

    public T getT() {
        return t;
    }

    public void setT(T t) {
        this.t = t;
    }

    public R getR() {
        return r;
    }

    public void setR(R r) {
        this.r = r;
    }
}
