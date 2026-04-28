package com.itangcent.model.generic;

import com.itangcent.model.generic.Layer2;
import com.itangcent.model.generic.Pair;

/**
 * Top layer: wraps T in Pair<String, T> before passing to Layer2.
 * Layer3<T> extends Layer2<Pair<String, T>>
 *
 * Full chain:
 *   Layer3<T> extends Layer2<Pair<String, T>>
 *   Layer2<T> extends Layer1<Wrapper<T>>
 *   Layer1<T> { T value; }
 *
 * So Layer1's value = Wrapper<Pair<String, T>>
 *
 * @param <T> the innermost type
 */
public class Layer3<T> extends Layer2<Pair<String, T>> {

    /**
     * layer3 flag
     */
    private Boolean flag;

    public Boolean getFlag() {
        return flag;
    }

    public void setFlag(Boolean flag) {
        this.flag = flag;
    }
}
