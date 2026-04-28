package com.itangcent.model.generic;

/**
 * Vote page query result item
 */
public class VotePageQueryVO {

    /**
     * vote id
     */
    private Long id;

    /**
     * vote label
     */
    private String label;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
