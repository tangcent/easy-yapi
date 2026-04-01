package com.itangcent.api.generic;

import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Case 2: Generic base controller with two type parameters
 */
public class TwoTypeBaseCtrl<T, R> {

    /**
     * query item
     */
    @GetMapping("/query")
    public Result<R> query() {
        return null;
    }

    /**
     * save item
     */
    @PostMapping("/save")
    public Result<R> save(@RequestBody T input) {
        return null;
    }
}
