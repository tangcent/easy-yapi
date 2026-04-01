package com.itangcent.api;

import com.itangcent.model.UserInfo;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * User Management APIs
 */
@RestController
@RequestMapping(value = "title-test")
public class TitleTestCtrl {

    /**
     * Get user by ID
     */
    @GetMapping("/user/{id}")
    public Result<UserInfo> getUser(@PathVariable("id") Long id) {
        return Result.success(new UserInfo());
    }

    /**
     * Create new user
     */
    @PostMapping("/user")
    public Result<UserInfo> createUser(@RequestBody UserInfo userInfo) {
        return Result.success(userInfo);
    }

    @GetMapping("/no-doc")
    public Result<UserInfo> noDocMethod() {
        return Result.success(new UserInfo());
    }
}

@RestController
@RequestMapping(value = "title-test")
class NoClassDocCtrl {

    /**
     * Method with doc but class has no doc
     */
    @GetMapping("/no-class-doc")
    public String noClassDocMethod() {
        return "ok";
    }
}

/**
 * Multi-line class doc
 * that spans several lines
 */
@RestController
@RequestMapping(value = "title-test")
class MultiLineDocCtrl {

    /**
     * Multi-line method doc
     * that describes the method
     */
    @GetMapping("/multi-line")
    public String multiLineMethod() {
        return "ok";
    }
}
