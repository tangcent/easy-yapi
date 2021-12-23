
package com.itangcent.api;


import com.itangcent.api.IUserApi;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * api interface
 */
@RestController
@RequestMapping(value = "user")
public class UserApiImpl implements IUserApi {

    @Override
    public Result<UserInfo> loginAuth(UserInfo userInfo) {
        return Result.success(userInfo);
    }
}
