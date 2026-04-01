package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.*;

/**
 * Fixture for same-named type param collision test.
 * Uses T as the type param name — same as many subclasses might use.
 */
@RequestMapping("/same-param")
public abstract class SameParamNameBase<T> {

    /**
     * get item
     */
    @GetMapping("/item")
    public abstract Result<T> getItem();
}
