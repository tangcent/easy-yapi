package com.itangcent.model.generic;

import com.itangcent.model.generic.GenericBase;

/**
 * Case 1: Extends GenericBase with concrete type String
 * GenericBase<T> { T data; }
 * StringChild extends GenericBase<String> → data should be String
 */
public class StringChild extends GenericBase<String> {

    /**
     * extra field in child
     */
    private Integer count;

    public Integer getCount() {
        return count;
    }

    public void setCount(Integer count) {
        this.count = count;
    }
}
