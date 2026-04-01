package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fixture for same-named type param collision test.
 * Binds T=UserInfo. The base class also uses T — same name, different binding.
 * This exercises the scoped collectSuperTypeBindings fix.
 */
@RestController
public class SameParamNameSub extends SameParamNameBase<UserInfo> {

    @Override
    public Result<UserInfo> getItem() {
        return null;
    }
}
