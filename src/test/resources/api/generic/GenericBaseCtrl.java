package com.itangcent.api.generic;

import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Case 1: Generic base controller with single type parameter
 */
public class GenericBaseCtrl<T> {

    /**
     * get item
     */
    @GetMapping("/item")
    public Result<T> getItem() {
        return null;
    }

    /**
     * create item
     */
    @PostMapping("/item")
    public Result<T> createItem(@RequestBody T item) {
        return null;
    }
}
