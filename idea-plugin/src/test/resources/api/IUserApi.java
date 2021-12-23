
package com.itangcent.api;


import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * api interface
 */
public interface IUserApi {

    @PostMapping(value = "/auth/loginAuth")
    Result<UserInfo> loginAuth(@RequestBody UserInfo userInfo);

}
