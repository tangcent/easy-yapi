package io.swagger.v3.oas.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Parameter {
    String name() default "";
    String description() default "";
    String in() default "";
    boolean required() default false;
    boolean hidden() default false;
    boolean deprecated() default false;
}
