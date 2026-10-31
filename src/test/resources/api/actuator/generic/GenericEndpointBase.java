package com.itangcent.springboot.demo.controller.generic;

import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;

/**
 * Generic Actuator endpoint base. The read operation returns the endpoint's
 * item type, so a subclass binding `T` exercises generic substitution in the
 * `export.after` lifecycle event.
 */
public abstract class GenericEndpointBase<T> {

    /**
     * read the generic item
     */
    @ReadOperation
    public T read() {
        return null;
    }
}
