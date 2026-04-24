package com.test.adapter;

import java.util.List;
import java.util.Map;

/**
 * Abstract base service with common operations.
 * @param <T> the entity type
 * @see JavaRepository
 */
public abstract class JavaComplexService<T> {
    /**
     * Validates the entity before saving.
     * @param entity the entity to validate
     * @return true if valid
     * @throws IllegalArgumentException if entity is null
     */
    public abstract boolean validate(T entity);

    /**
     * Converts entity to a map representation.
     * @param entity the entity to convert
     * @return map of field names to values
     */
    public Map<String, Object> toMap(T entity) { return null; }

    /**
     * Inner class for pagination.
     * @param <E> the element type
     */
    public static class Page<E> {
        /** The page content. */
        public List<E> content;
        /** The total number of elements. */
        public long totalElements;
        /** The current page number. */
        public int pageNumber;
    }
}
