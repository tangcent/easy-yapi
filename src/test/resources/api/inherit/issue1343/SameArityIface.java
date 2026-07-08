package com.itangcent.api.inherit.issue1343;

import com.itangcent.api.inherit.issue1343.IQuery;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Issue #1343 fixture: same-name methods with SAME arity.
 * The impl overrides both, so findSuperMethods() resolves each override to its
 * matching interface declaration correctly — the name+arity fallback is NOT
 * triggered. The fallback's known limitation (cannot distinguish same-arity
 * same-name overloads) only applies when findSuperMethods() returns empty.
 *
 *  - query(String)    -> arity 1, NO mapping (declared first)
 *  - query(LQ)        -> arity 1, @GetMapping (declared second)
 */
@RequestMapping("/api")
public interface SameArityIface<LQ extends IQuery> {

    Result<String> query(String name);

    @GetMapping("/query")
    Result<String> query(LQ query);
}
