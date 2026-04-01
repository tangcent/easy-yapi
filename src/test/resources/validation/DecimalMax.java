/*
 * Bean Validation API
 *
 * License: Apache License, Version 2.0
 * See the license.txt file in the root directory or <http://www.apache.org/licenses/LICENSE-2.0>.
 */
package javax.validation.constraints;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.CONSTRUCTOR;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import javax.validation.Constraint;
import javax.validation.Payload;
import javax.validation.constraints.DecimalMax.List;

/**
 * The annotated element must be a number whose value must be lower or
 * equal to the specified maximum.
 * <p>
 * Supported types are:
 * <ul>
 *     <li>{@code BigDecimal}</li>
 *     <li>{@code BigInteger}</li>
 *     <li>{@code CharSequence}</li>
 *     <li>{@code byte}, {@code short}, {@code int}, {@code long}, and their respective
 *     wrappers</li>
 * </ul>
 * Note that {@code double} and {@code float} are not supported due to rounding errors
 * (some providers might provide some approximative support).
 * <p>
 * {@code null} elements are considered valid.
 *
 * @author Emmanuel Bernard
 */
@Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
@Retention(RUNTIME)
@Repeatable(List.class)
@Documented
@Constraint(validatedBy = { })
public @interface DecimalMax {

    String message() default "{javax.validation.constraints.DecimalMax.message}";

    Class<?>[] groups() default { };

    Class<? extends Payload>[] payload() default { };

    /**
     * The {@code String} representation of the max value according to the
     * {@code BigDecimal} string representation.
     *
     * @return value the element must be lower or equal to
     */
    String value();

    /**
     * Specifies whether the specified maximum is inclusive or exclusive.
     * By default, it is inclusive.
     *
     * @return {@code true} if the value must be lower or equal to the specified maximum,
     *         {@code false} if the value must be lower
     *
     * @since 1.1
     */
    boolean inclusive() default true;

    /**
     * Defines several {@link DecimalMax} annotations on the same element.
     *
     * @see DecimalMax
     */
    @Target({ METHOD, FIELD, ANNOTATION_TYPE, CONSTRUCTOR, PARAMETER, TYPE_USE })
    @Retention(RUNTIME)
    @Documented
    @interface List {

        DecimalMax[] value();
    }
}
