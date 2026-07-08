package com.itangcent.api.inherit.issue1343;

import com.itangcent.api.inherit.issue1343.IQuery;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Issue #1343 negative-test fixture: a bounded-generic interface declaring
 * {@code process(LQ)} with {@code @GetMapping}.
 *
 * Used together with {@link UnrelatedBoundedImpl} to verify the type-compatibility
 * guard in {@code ResolvedMethod.superMethod()}'s name+arity fallback.
 */
@RequestMapping("/api")
public interface UnrelatedBoundedIface<LQ extends IQuery> {

    @GetMapping("/process")
    Result<String> process(LQ query);
}
