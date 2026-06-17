package com.itangcent.fastjson;

import com.alibaba.fastjson.annotation.JSONField;

public class FastjsonDTO {

    @JSONField(value = "product_id")
    private Long id;

    @JSONField(value = "product_name")
    private String name;

    private String description;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
