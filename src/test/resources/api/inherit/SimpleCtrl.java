package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.*;

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
