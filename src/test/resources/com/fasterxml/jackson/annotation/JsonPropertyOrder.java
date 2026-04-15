package com.fasterxml.jackson.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonPropertyOrder {
    String[] value() default {};
    boolean alphabetic() default false;
}
