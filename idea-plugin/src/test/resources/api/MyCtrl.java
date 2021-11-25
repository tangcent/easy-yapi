package com.itangcent.api;


import com.itangcent.api.BaseController;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.FakeMapping;
import org.springframework.web.bind.annotation.MyPostMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * demo for custom annotations
 */
@RestController
public class MyCtrl extends BaseController {

    /**
     * post
     */
    @MyPostMapping("/myPost")
    public Result<String> post() {
        return Result.success("yes");
    }

    /**
     * postWithParam
     */
    @MyPostMapping(value = "/myPostWithParam", params = "name")
    public Result<String> postWithParam() {
        return Result.success("yes");
    }

    /**
     * fake
     */
    @FakeMapping("/fake")
    public Result<String> fake() {
        return Result.success("no");
    }

}
