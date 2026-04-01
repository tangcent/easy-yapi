package com.itangcent.model.generic;

/**
 * Case 2: Generic base class with two type parameters
 */
public class TwoTypeBase<T, R> {

    /**
     * first type param
     */
    private T first;

    /**
     * second type param
     */
    private R second;

    public T getFirst() {
        return first;
    }

    public void setFirst(T first) {
        this.first = first;
    }

    public R getSecond() {
        return second;
    }

    public void setSecond(R second) {
        this.second = second;
    }
}
