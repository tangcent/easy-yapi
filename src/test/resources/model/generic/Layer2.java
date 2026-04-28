package com.itangcent.model.generic;

import com.itangcent.model.generic.Layer1;
import com.itangcent.model.generic.Wrapper;

/**
 * Middle layer: wraps T in Wrapper<T> before passing to Layer1.
 * Layer2<T> extends Layer1<Wrapper<T>>
 *
 * So Layer1's T becomes Wrapper<T>.
 *
 * @param <T> the inner type
 */
public class Layer2<T> extends Layer1<Wrapper<T>> {

    /**
     * layer2 tag
     */
    private String tag;

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }
}
