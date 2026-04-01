package com.itangcent.springboot.demo.client;

import com.itangcent.annotation.Public;
import com.itangcent.dto.IResult;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient("user")
@RequestMapping(value = "/user")
public interface UserClient {

    /**
     * say hello
     * not update anything
     */
    @Public
    @RequestMapping(value = "/index")
    public String greeting();

    /**
     * get user info
     *
     * @param id user id
     * @folder update-apis
     * @undone
     */
    @Deprecated
    @GetMapping("/get/{id}")
    public IResult get(@PathVariable("id") Long id);

    /**
     * create an user
     */
    @PostMapping("/add")
    public Result<UserInfo> add(@RequestBody UserInfo userInfo);

    /**
     * update user info
     */
    @PutMapping("update")
    public IResult update(UserInfo userInfo);
}
