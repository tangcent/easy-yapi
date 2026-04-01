package com.itangcent.model.generic;

/**
 * Case 2: Partially binds TwoTypeBase - binds R to String, passes X through as T
 * TwoTypeBase<T, R> { T first; R second; }
 * MiddleChild<X> extends TwoTypeBase<X, String>
 */
public class MiddleChild<X> extends TwoTypeBase<X, String> {

    /**
     * middle-level field
     */
    private String middleName;

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }
}
