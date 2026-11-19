package com.itangcent.model.binding;

/**
 * Nested query object: {@code status} is writable, {@code label} is not.
 */
public class QueryFilter {

    private Integer status;

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    /** read-only: computed, no field and no setter */
    public String getLabel() {
        return "status-" + status;
    }
}
