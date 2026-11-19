package com.itangcent.model.binding;

/**
 * Immutable query object: Spring binds {@code name} through the constructor.
 */
public class ConstructorQuery {

    private final String name;

    public ConstructorQuery(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    /** read-only: computed, no field and no setter */
    public String getUpperName() {
        return name == null ? null : name.toUpperCase();
    }
}
