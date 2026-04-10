package com.itangcent.api.inherit;

import com.itangcent.model.IResult;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller with overloaded methods that have the same parameter count
 * but different parameter types.
 */
@RestController
@RequestMapping("file")
public class OverloadedCtrl {

    /**
     * Upload with single file
     */
    @PostMapping("/add")
    public IResult add(UserInfo userInfo,
                       @RequestParam(name = "profileImg") MultipartFile profileImg) {
        return Result.success("ok");
    }

    /**
     * Upload with multiple files
     */
    @PostMapping("/add2")
    public IResult add(UserInfo userInfo,
                       @RequestParam(name = "profileImg") MultipartFile[] profileImgs) {
        return Result.success("ok");
    }
}
