package com.itangcent.yapi;

public class ItemDTO {

    /**
     * Item id
     *
     * @mock 123
     */
    private Long id;

    /**
     * Item name
     *
     * @mock test-item
     */
    private String name;

    /**
     * Item description
     */
    private String description;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
