package com.itangcent.model.generic;

/**
 * Case 1: Generic base class with single type parameter
 */
public class GenericBase<T> {

    /**
     * the data payload
     */
    private T data;

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }
}
