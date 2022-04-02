
package com.itangcent.api;


import com.itangcent.api.IUserApi;
import com.itangcent.model.Model;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.RestController;

/**
 * api interface
 */
@RestController
public class UserApiImpl implements IUserApi<UserInfo, Model> {

    @Override
    public Result<Model> loginAuth(UserInfo userInfo) {
        return Result.success(userInfo);
    }
}
