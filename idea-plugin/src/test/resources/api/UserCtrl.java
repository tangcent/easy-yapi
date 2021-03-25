package com.itangcent.api;

import com.itangcent.common.annotation.Public;
import com.itangcent.model.*;
import org.springframework.web.bind.annotation.*;

/**
 * apis about user
 * access user info
 *
 * @module users
 */
@RestController
@RequestMapping(value = "user")
public class UserCtrl extends BaseController {

    /**
     * say hello
     * not update anything
     */
    @Public
    @RequestMapping(value = "/greeting")
    public String greeting() {
        return "hello world";
    }


    /**
     * get user info
     *
     * @param id user id
     * @folder update-apis
     * @undone
     */
    @Deprecated
    @GetMapping("/get/{id}")
    public IResult get(@PathVariable("id") Long id) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(id);
        userInfo.setName("Tony Stark");
        userInfo.setAge(45);
        return Result.success(userInfo);
    }
}
