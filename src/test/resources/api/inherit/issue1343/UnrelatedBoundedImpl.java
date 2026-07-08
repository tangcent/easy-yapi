package com.itangcent.api.inherit.issue1343;

import com.itangcent.api.inherit.issue1343.ConcreteQuery;
import com.itangcent.api.inherit.issue1343.UnrelatedBoundedIface;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issue #1343 negative-test fixture: implements {@link UnrelatedBoundedIface}.
 *
 * {@code process(ConcreteQuery)} is a genuine override of the interface method —
 * the name+arity fallback links it (ConcreteQuery is compatible with LQ's binding)
 * and it inherits {@code @GetMapping}.
 *
 * {@code process(String)} is a NEW method with a different signature. It shares
 * name + arity (1) with {@code UnrelatedBoundedIface.process(LQ)}, so without the
 * type-compatibility guard the fallback would falsely link them and inherit
 * {@code @GetMapping}. The guard must reject the candidate (String is incompatible
 * with ConcreteQuery, the resolved binding of LQ), so {@code process(String)}
 * gets NO mapping.
 */
@RestController
public class UnrelatedBoundedImpl implements UnrelatedBoundedIface<ConcreteQuery> {

    @Override
    public Result<String> process(ConcreteQuery query) {
        return Result.success("ok");
    }

    public Result<String> process(String name) {
        return Result.success("ok");
    }
}
