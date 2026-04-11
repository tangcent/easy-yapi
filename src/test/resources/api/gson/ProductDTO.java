package com.itangcent.gson;

import com.google.gson.annotations.SerializedName;
import com.google.gson.annotations.Expose;

public class ProductDTO {

    @SerializedName("product_id")
    private Long id;

    @SerializedName("product_name")
    private String name;

    @Expose(serialize = false)
    private String internalCode;

    private Double price;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getInternalCode() { return internalCode; }
    public void setInternalCode(String internalCode) { this.internalCode = internalCode; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
}
