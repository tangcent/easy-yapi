package com.itangcent.model.generic;

import com.itangcent.model.generic.BaseEntity;

/**
 * Concrete entity: BaseEntity<Long>
 *
 * Full chain:
 *   UserEntity extends BaseEntity<Long>
 *   BaseEntity<ID> implements Identifiable<ID>, Named
 *   Identifiable<ID> { ID getId(); }
 *   Named { String getName(); }
 *
 * So `id` should resolve to Long.
 */
public class UserEntity extends BaseEntity<Long> {

    /**
     * user email
     */
    private String email;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
