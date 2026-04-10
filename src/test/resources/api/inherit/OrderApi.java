package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/order")
public interface OrderApi {

    /**
     * create an order
     */
    @PostMapping("/create")
    Result<String> createOrder(@RequestBody String orderData);
}
