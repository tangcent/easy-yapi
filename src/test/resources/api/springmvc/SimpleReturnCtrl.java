package com.itangcent.springboot.demo.controller;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/simple")
public class SimpleReturnCtrl {

    @GetMapping("/user")
    public Result<UserInfo> getUser() {
        return Result.success(new UserInfo());
    }
}
