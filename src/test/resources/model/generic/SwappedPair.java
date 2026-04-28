package com.itangcent.model.generic;

import com.itangcent.model.generic.Pair;

/**
 * Swaps the type parameters when passing to parent.
 * SwappedPair<X, Y> extends Pair<Y, X>
 *
 * This tests that type parameter mapping handles swapped positions correctly.
 *
 * @param <X> maps to parent's B
 * @param <Y> maps to parent's A
 */
public class SwappedPair<X, Y> extends Pair<Y, X> {

    /**
     * swap tag
     */
    private Boolean swapped;

    public Boolean getSwapped() {
        return swapped;
    }

    public void setSwapped(Boolean swapped) {
        this.swapped = swapped;
    }
}
