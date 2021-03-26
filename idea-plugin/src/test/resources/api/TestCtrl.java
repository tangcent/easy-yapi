package com.itangcent.api;

import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


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
     * @module test-only
     * @real_return {@link Result<UserDto>}
     */
    @RequestMapping("/header")
    public String header(
            @RequestHeader("token") String token) {
        return token;
    }

}
