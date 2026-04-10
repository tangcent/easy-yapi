package io.swagger.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD, ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiModelProperty {
    String value() default "";
    String name() default "";
    String notes() default "";
    boolean required() default false;
    boolean hidden() default false;
}
