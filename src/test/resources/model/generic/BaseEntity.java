package com.itangcent.model.generic;

import com.itangcent.model.generic.Identifiable;
import com.itangcent.model.generic.Named;

/**
 * Abstract base entity implementing both Identifiable and Named.
 * Diamond inheritance: BaseEntity<ID> implements Identifiable<ID>, Named
 *
 * @param <ID> the identifier type
 */
public abstract class BaseEntity<ID> implements Identifiable<ID>, Named {

    /**
     * entity id
     */
    private ID id;

    /**
     * entity name
     */
    private String name;

    /**
     * creation timestamp
     */
    private Long createdAt;

    @Override
    public ID getId() {
        return id;
    }

    public void setId(ID id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
