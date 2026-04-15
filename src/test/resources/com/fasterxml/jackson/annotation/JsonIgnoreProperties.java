package com.fasterxml.jackson.annotation;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonIgnoreProperties {
    String[] value() default {};
    boolean ignoreUnknown() default false;
}
