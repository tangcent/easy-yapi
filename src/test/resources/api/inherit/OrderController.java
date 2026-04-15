package com.itangcent.api.inherit;

import com.itangcent.api.inherit.OrderApi;
import com.itangcent.api.inherit.SendAuditLog;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.RestController;

/**
 * Order controller that implements OrderApi.
 * createOrder is annotated with @SendAuditLog (not a mapping annotation)
 * to verify that non-mapping annotations don't cause NPE.
 */
@RestController
public class OrderController implements OrderApi {

    @Override
    @SendAuditLog("order.create")
    public Result<String> createOrder(String orderData) {
        return Result.success("created");
    }
}
