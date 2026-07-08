package com.itangcent.api.inherit.issue1343;

import com.itangcent.api.inherit.issue1343.IQuery;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Issue #1343 fixture: same-name methods with DIFFERENT arity.
 * name+arity matching must disambiguate these correctly.
 *  - query(LQ)            -> arity 1, @GetMapping
 *  - query(LQ, String)    -> arity 2, @PostMapping
 */
@RequestMapping("/api")
public interface OverloadIface<LQ extends IQuery> {

    @GetMapping("/query")
    Result<String> query(LQ query);

    @PostMapping("/query2")
    Result<String> query(LQ query, String extra);
}
