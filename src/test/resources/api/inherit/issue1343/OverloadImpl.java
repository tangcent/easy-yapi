package com.itangcent.api.inherit.issue1343;

import com.itangcent.api.inherit.issue1343.ConcreteQuery;
import com.itangcent.api.inherit.issue1343.OverloadIface;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issue #1343 fixture: implements OverloadIface<ConcreteQuery>, overriding both
 * arity-1 and arity-2 query methods without re-declaring mapping annotations.
 */
@RestController
public class OverloadImpl implements OverloadIface<ConcreteQuery> {

    @Override
    public Result<String> query(ConcreteQuery query) {
        return Result.success("ok");
    }

    @Override
    public Result<String> query(ConcreteQuery query, String extra) {
        return Result.success("ok");
    }
}
