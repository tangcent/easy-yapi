package com.fasterxml.jackson.annotation;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface JsonUnwrapped {
    String prefix() default "";
    String suffix() default "";
    boolean enabled() default true;
}
