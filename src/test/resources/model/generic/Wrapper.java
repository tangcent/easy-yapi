package com.itangcent.model.generic;

/**
 * Simple generic wrapper with one type parameter.
 *
 * @param <T> the wrapped type
 */
public class Wrapper<T> {

    /**
     * the wrapped value
     */
    private T value;

    /**
     * wrapper label
     */
    private String label;

    public T getValue() {
        return value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
