package com.itangcent.springboot.demo.controller;


import com.itangcent.common.dto.*;
import org.springframework.web.bind.annotation.*;


/**
 * test apis
 */
@RestController
@RequestMapping(value = "/test")
public class TestCtrl extends BaseController {

    /**
     * test RequestHeader
     *
     * @param token input token
     * @return token output
     * @real_return {@link Result<UserDto>}
     */
    @RequestMapping("/header")
    public String header(
            @RequestHeader("token") String token) {
        return token;
    }

}
