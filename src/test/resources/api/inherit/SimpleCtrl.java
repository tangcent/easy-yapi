package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case 1: Simple controller with directly annotated methods, no inheritance.
 */
@RestController
@RequestMapping("/simple")
public class SimpleCtrl {

    /**
     * list items
     */
    @GetMapping("/list")
    public Result<UserInfo> list() {
        return null;
    }

    /**
     * create item
     */
    @PostMapping("/create")
    public Result<UserInfo> create(@RequestBody UserInfo userInfo) {
        return null;
    }
}
