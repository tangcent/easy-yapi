package io.swagger.v3.oas.annotations.media;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Schema {
    String name() default "";
    String description() default "";
    RequiredMode requiredMode() default RequiredMode.AUTO;
    boolean hidden() default false;
    boolean deprecated() default false;

    enum RequiredMode {
        AUTO,
        REQUIRED,
        NOT_REQUIRED
    }
}
