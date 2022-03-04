
package com.itangcent.api;


import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.ArrayList;

/**
 * api interface
 */
public interface IUserApi<Req, Res> {

    @PostMapping(value = "/auth/loginAuth")
    Result<Res> loginAuth(@RequestBody Req req);

    /**
     * A default api
     * It is not necessary to implement it
     *
     * @param req request
     * @return response
     */
    @PostMapping(value = "/default")
    default Result<List<Res>> defaultApi(@RequestBody List<Req> req) {
        return Result.success(new ArrayList<>());
    }
}
