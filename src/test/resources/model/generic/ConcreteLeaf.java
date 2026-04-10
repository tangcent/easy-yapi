package com.itangcent.model.generic;

import com.itangcent.model.generic.MiddleChild;

/**
 * Case 2: Fully concrete - binds X to Long
 * MiddleChild<X> extends TwoTypeBase<X, String>
 * ConcreteLeaf extends MiddleChild<Long>
 * → first should be Long, second should be String
 */
public class ConcreteLeaf extends MiddleChild<Long> {

    /**
     * leaf-level field
     */
    private Boolean active;

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
