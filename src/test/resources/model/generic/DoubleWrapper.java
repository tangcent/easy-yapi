package com.itangcent.model.generic;

import com.itangcent.model.generic.Wrapper;

/**
 * Wraps T in Wrapper<T> when passing to parent.
 * DoubleWrapper<T> extends Wrapper<Wrapper<T>>
 *
 * This tests the case where a type parameter is wrapped
 * in another generic type before being passed to the parent.
 *
 * @param <T> the inner type
 */
public class DoubleWrapper<T> extends Wrapper<Wrapper<T>> {

    /**
     * extra info
     */
    private String extra;

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }
}
