package com.itangcent.springboot.demo.client.generic;

import com.itangcent.model.UserInfo;
import com.itangcent.springboot.demo.client.generic.BaseClient;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Concrete Feign client binding the inherited `T` to [UserInfo].
 */
@FeignClient("generic")
@RequestMapping("/generic")
public interface UserClient extends BaseClient<UserInfo> {
}
