package com.itangcent.api.inherit.issue1343;

/**
 * Fixture for declaredFields() exclusion test: declares two own fields.
 * A subclass must NOT see these in its own declaredFields().
 */
public class FieldBase {

    private String baseFieldA;
    private Integer baseFieldB;
}
