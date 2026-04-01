package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case 2 sub: extends AnnotatedBaseCtrl, overrides methods WITHOUT adding mapping annotations.
 * Annotations should be inherited from the base class.
 */
@RestController
public class PlainSubCtrl extends AnnotatedBaseCtrl {

    @Override
    public Result<UserInfo> getItem() {
        return null;
    }

    @Override
    public Result<UserInfo> saveItem(UserInfo item) {
        return null;
    }
}
