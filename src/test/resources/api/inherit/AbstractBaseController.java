package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/base")
public class AbstractBaseController {

    @GetMapping("/item")
    public Result<UserInfo> getItem() {
        return null;
    }

    @PostMapping("/item")
    public Result<UserInfo> saveItem(@RequestBody UserInfo item) {
        return null;
    }
}
