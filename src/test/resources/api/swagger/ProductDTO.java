package com.itangcent.swagger;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

@ApiModel(value = "Product", description = "Product information")
public class ProductDTO {

    @ApiModelProperty(value = "Product ID", required = true)
    private Long id;

    @ApiModelProperty(value = "Product name", required = true)
    private String name;

    @ApiModelProperty(value = "Product price")
    private Double price;

    @ApiModelProperty(value = "Product description", notes = "Detailed product description")
    private String description;

    @ApiModelProperty(value = "Internal notes", hidden = true)
    private String internalNotes;

    @ApiModelProperty(value = "SKU code", name = "skuCode", example = "SKU-0001")
    private String sku;

    // No example: must not gain an empty one from the `field.demo` rule.
    @ApiModelProperty(value = "Stock count")
    private Integer stock;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInternalNotes() { return internalNotes; }
    public void setInternalNotes(String internalNotes) { this.internalNotes = internalNotes; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
}
