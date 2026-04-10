package com.itangcent.api.inherit;

import com.itangcent.api.inherit.GenericIface;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.RestController;

/**
 * Case 4: implements GenericIface with concrete types, overrides without annotations.
 */
@RestController
public class GenericIfaceImpl implements GenericIface<UserInfo, String> {

    @Override
    public Result<String> query() {
        return null;
    }

    @Override
    public Result<String> save(UserInfo input) {
        return null;
    }
}
