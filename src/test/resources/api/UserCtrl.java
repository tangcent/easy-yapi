package com.itangcent.api;

import com.itangcent.annotation.Public;
import com.itangcent.api.BaseController;
import com.itangcent.model.IResult;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

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

    /**
     * create an user
     */
    @PostMapping({"/add", "/admin/add"})
    public Result<UserInfo> create(@RequestBody UserInfo userInfo) {
        return Result.success(userInfo);
    }

    /**
     * update user info
     */
    @PutMapping("/update")
    public Result<UserInfo> update(@ModelAttribute UserInfo userInfo) {
        return Result.success(userInfo);
    }

    /**
     * get user by cookie
     */
    @GetMapping("/by-cookie")
    public Result<UserInfo> getByCookie(
            @org.springframework.web.bind.annotation.CookieValue("sessionId") String sessionId) {
        UserInfo userInfo = new UserInfo();
        userInfo.setName("Cookie User");
        return Result.success(userInfo);
    }

    /**
     * get from session
     */
    @GetMapping("/from-session")
    public Result<UserInfo> getFromSession(
            @org.springframework.web.bind.annotation.SessionAttribute("userId") Long userId) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        return Result.success(userInfo);
    }

    /**
     * update user with request body
     * uses @RequestMapping without method, should default to POST
     */
    @RequestMapping("/update-with-body")
    public Result<UserInfo> updateWithBody(@RequestBody UserInfo userInfo) {
        return Result.success(userInfo);
    }

    /**
     * simple request without body
     * uses @RequestMapping without method, should default to GET
     */
    @RequestMapping("/simple")
    public Result<UserInfo> simpleRequest() {
        return Result.success(new UserInfo());
    }

    /**
     * explicit GET with request body
     * uses @GetMapping explicitly, should remain GET even with @RequestBody
     */
    @GetMapping("/explicit-get-with-body")
    public Result<UserInfo> explicitGetWithBody(@RequestBody UserInfo userInfo) {
        return Result.success(userInfo);
    }

    /**
     * update with model attribute
     * uses @RequestMapping without method, should default to POST due to @ModelAttribute
     */
    @RequestMapping("/update-with-model")
    public Result<UserInfo> updateWithModel(@ModelAttribute UserInfo userInfo) {
        return Result.success(userInfo);
    }

    /**
     * Nested API for user profile operations
     * 
     * @module user-profiles
     */
    @RestController
    @RequestMapping(value = "/profile")
    public static class ProfileApi extends BaseController {
        
        /**
         * Get user profile settings
         * 
         * @param userId The ID of the user whose profile settings to retrieve
         * @return User profile settings
         */
        @GetMapping("/settings/{userId}")
        public Result<UserInfo> getProfileSettings(@PathVariable("userId") Long userId) {
            UserInfo userInfo = new UserInfo();
            userInfo.setId(userId);
            userInfo.setName("Profile User");
            userInfo.setAge(30);
            return Result.success(userInfo);
        }
    }

}