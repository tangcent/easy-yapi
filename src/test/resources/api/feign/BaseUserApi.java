package com.itangcent.api.feign;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Base API interface with mapping annotations.
 * UserFeignClient extends this interface.
 */
@RequestMapping("/base-user")
public interface BaseUserApi {

    /**
     * Get user by ID
     */
    @GetMapping("/user/{userId}")
    Result<UserInfo> getUserById(@PathVariable("userId") Long userId);
}
