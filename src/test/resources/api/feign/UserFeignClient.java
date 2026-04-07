package com.itangcent.api.feign;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client that extends BaseUserApi.
 * Overrides getUserById without re-declaring @GetMapping.
 */
@FeignClient("user-service")
public interface UserFeignClient extends BaseUserApi {

    @Override
    Result<UserInfo> getUserById(Long userId);

    @GetMapping("/user/list")
    Result<UserInfo> getUserList(@RequestParam("name") String name);
}
