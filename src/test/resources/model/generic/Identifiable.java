package com.itangcent.model.generic;

/**
 * Interface with a type parameter for the ID type.
 *
 * @param <ID> the identifier type
 */
public interface Identifiable<ID> {

    ID getId();
}
