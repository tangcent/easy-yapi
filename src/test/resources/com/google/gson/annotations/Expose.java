package com.google.gson.annotations;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Expose {
    boolean serialize() default true;
    boolean deserialize() default true;
}
