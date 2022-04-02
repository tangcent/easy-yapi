
package com.itangcent.api;


import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;

/**
 * api interface
 */
@RequestMapping(value = "user")
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
