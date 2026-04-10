package com.itangcent.api.inherit;

import com.itangcent.api.inherit.PlainBaseCtrl;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case 3 sub: extends PlainBaseCtrl, overrides methods WITH mapping annotations.
 */
@RestController
@RequestMapping("/annotated-sub")
public class AnnotatedSubCtrl extends PlainBaseCtrl {

    @Override
    @GetMapping("/item")
    public Result<UserInfo> getItem() {
        return null;
    }

    @Override
    @PostMapping("/item")
    public Result<UserInfo> saveItem(@RequestBody UserInfo item) {
        return null;
    }
}
