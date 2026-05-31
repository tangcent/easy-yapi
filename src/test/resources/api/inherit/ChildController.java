package com.itangcent.api.inherit;

import com.itangcent.api.inherit.AbstractBaseController;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChildController extends AbstractBaseController {

    @GetMapping("/own")
    public Result<UserInfo> getOwn() {
        return null;
    }
}
