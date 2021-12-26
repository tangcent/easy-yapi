package com.itangcent.springboot.demo.client;

import com.itangcent.constant.UserType;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import feign.Headers;
import feign.Param;
import feign.RequestLine;
import org.springframework.cloud.openfeign.FeignClient;

import java.util.List;


@FeignClient("user")
public interface PrimitiveUserClient {

    /**
     * create an user
     *
     * @param user user info
     */
    @RequestLine("POST /user")
    @Headers("content-type: application/x-www-form-urlencoded")
    Result<UserInfo> add(UserInfo user);

    /**
     * list users
     *
     * @param user  user info
     * @param token my token
     * @param type  user type {@link UserType#type}
     * @param id    user id
     * @return
     */
    @RequestLine("POST /user/list/{type}")
    @Headers({"content-type: application/json",
            "token: {token} ",
            "id: {id} "
    })
    Result<List<UserInfo>> list(UserInfo user,
                                @Param("token") String token,
                                @Param("type") String type,
                                @Param("id") String id);
}
