package com.test.adapter;

import java.util.List;
import java.util.Map;

/**
 * Generic repository interface.
 * @param <T> the entity type
 * @param <ID> the identifier type
 */
public interface JavaRepository<T, ID> {
    /**
     * Finds an entity by its identifier.
     * @param id the entity identifier
     * @return the entity, or null if not found
     */
    T findById(ID id);

    /**
     * Saves the given entity.
     * @param entity the entity to save
     * @return the saved entity
     */
    T save(T entity);

    /**
     * Finds all entities.
     * @return list of all entities
     */
    List<T> findAll();
}
