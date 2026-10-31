package com.itangcent.springboot.demo.client.generic;

import com.itangcent.model.UserInfo;
import feign.RequestLine;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Generic Feign client contract. Each method returns the client's item type,
 * so a subclass binding `T` to a concrete class exercises generic substitution
 * on both the native (`@RequestLine`) and Spring MVC (`@GetMapping`) branches.
 */
public interface BaseClient<T> {

    /**
     * native feign: get the generic item
     */
    @RequestLine("GET /generic/native")
    T nativeGet();

    /**
     * spring mapping: get the generic item
     */
    @GetMapping("/generic/spring")
    T springGet();
}
