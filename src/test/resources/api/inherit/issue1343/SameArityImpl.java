package com.itangcent.api.inherit.issue1343;

import com.itangcent.api.inherit.issue1343.ConcreteQuery;
import com.itangcent.api.inherit.issue1343.SameArityIface;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issue #1343 fixture: implements SameArityIface<ConcreteQuery>.
 * Both overloads are overridden, so findSuperMethods() resolves each to its
 * matching interface declaration via the fast path (not the name+arity fallback).
 */
@RestController
public class SameArityImpl implements SameArityIface<ConcreteQuery> {

    @Override
    public Result<String> query(String name) {
        return Result.success("ok");
    }

    @Override
    public Result<String> query(ConcreteQuery query) {
        return Result.success("ok");
    }
}
