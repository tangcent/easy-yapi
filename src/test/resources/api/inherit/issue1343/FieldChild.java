package com.itangcent.api.inherit.issue1343;

/**
 * Fixture for declaredFields() exclusion test: extends FieldBase and declares
 * one own field. declaredFields() must return ONLY {childField}, not the
 * inherited {baseFieldA, baseFieldB}.
 */
public class FieldChild extends FieldBase {

    private String childField;
}
