package com.itangcent.client;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Order Service Client
 */
@FeignClient(name = "order-service")
@RequestMapping("/title-test")
public interface TitleTestClient {

    /**
     * Get order by ID
     */
    @GetMapping("/{id}")
    Result<UserInfo> getById(@PathVariable("id") Long id);

    /**
     * Create order
     */
    @PostMapping
    Result<UserInfo> create(@RequestBody UserInfo userInfo);

    @GetMapping("/no-doc")
    String noDocMethod();
}
