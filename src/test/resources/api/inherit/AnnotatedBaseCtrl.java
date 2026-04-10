package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

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
