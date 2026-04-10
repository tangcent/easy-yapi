package com.itangcent.api.inherit;

import com.itangcent.api.inherit.AnnotatedGenericBase;
import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.RestController;

/**
 * Composite case: concrete controller binding generics, overriding without annotations.
 * AnnotatedGenericBase<Req,Res> has @GetMapping/@PostMapping on methods.
 * PlainGenericSub binds Req=UserInfo, Res=String, overrides without annotations.
 */
@RestController
public class PlainGenericSub extends AnnotatedGenericBase<UserInfo, String> {

    @Override
    public Result<String> query() {
        return null;
    }

    @Override
    public Result<String> save(UserInfo input) {
        return null;
    }
}
