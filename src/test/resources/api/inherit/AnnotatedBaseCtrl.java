package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.*;

/**
 * Case 2 base: abstract class with mapping annotations on methods.
 */
@RequestMapping("/annotated-base")
public abstract class AnnotatedBaseCtrl {

    /**
     * get item
     */
    @GetMapping("/item")
    public abstract Result<UserInfo> getItem();

    /**
     * save item
     */
    @PostMapping("/item")
    public abstract Result<UserInfo> saveItem(@RequestBody UserInfo item);
}
