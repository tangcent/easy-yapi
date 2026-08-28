package com.itangcent.mybatisplus;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * apis for paginated users, returning MyBatis-Plus IPage
 */
@RestController
@RequestMapping("/page")
public class PageController {

    /**
     * list users with pagination
     *
     * @return paged user list
     */
    @GetMapping("/users")
    public Result<IPage<UserInfo>> listUsers() {
        return Result.success(null);
    }
}
