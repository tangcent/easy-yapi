package com.itangcent.swagger3;

import com.itangcent.swagger3.OrderDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.Tags;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Order Management")
@RestController
@RequestMapping("/order")
public class OrderController {

    @Operation(summary = "Get order by ID", description = "Retrieves an order by its unique identifier")
    @GetMapping("/get/{id}")
    public OrderDTO getOrder(
            @Parameter(description = "Order ID", required = true) @PathVariable("id") Long id) {
        return new OrderDTO();
    }

    @Operation(summary = "Create a new order", tags = "order")
    @PostMapping("/create")
    public OrderDTO createOrder(@RequestBody OrderDTO order) {
        return order;
    }

    @Operation(summary = "List all orders")
    @GetMapping("/list")
    public java.util.List<OrderDTO> listOrders(
            @Parameter(description = "page number", required = true) Integer page,
            @Parameter(description = "page size") Integer size) {
        return java.util.Collections.emptyList();
    }

    @Hidden
    @GetMapping("/internal")
    public String internalEndpoint() {
        return "internal";
    }
}
