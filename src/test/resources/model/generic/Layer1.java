package com.itangcent.model.generic;

/**
 * Bottom layer: holds a value of type T.
 *
 * @param <T> the value type
 */
public class Layer1<T> {

    /**
     * the value
     */
    private T value;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }
}
