package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.*;

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
