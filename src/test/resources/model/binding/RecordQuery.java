package com.itangcent.model.binding;

/**
 * Record query object: Spring binds the components through the canonical constructor.
 */
public record RecordQuery(String name, Integer size) {

    /** read-only: computed, no component */
    public String getSummary() {
        return name + ":" + size;
    }
}
