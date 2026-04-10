package com.itangcent.swagger3;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Order information")
public class OrderDTO {

    @Schema(name = "orderId", description = "Order ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Order name", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @Schema(description = "Total amount")
    private Double amount;

    @Schema(description = "Order status")
    private String status;

    @Schema(description = "Internal field", hidden = true)
    private String internalField;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getInternalField() { return internalField; }
    public void setInternalField(String internalField) { this.internalField = internalField; }
}
