package jakarta.validation.constraints;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NotBlank {
    String message() default "";
    Class<?>[] groups() default {};
}
