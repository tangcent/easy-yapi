package com.itangcent.api.inherit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom annotation for audit logging — NOT a Spring mapping annotation.
 * Used to verify that non-mapping annotations don't cause NPE in RequestMappingResolver.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SendAuditLog {
    String value() default "";
}
