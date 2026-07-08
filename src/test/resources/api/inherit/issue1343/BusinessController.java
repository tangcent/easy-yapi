package com.itangcent.api.inherit.issue1343;

import com.itangcent.api.inherit.issue1343.ConcreteQuery;
import com.itangcent.api.inherit.issue1343.IController;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.RestController;

/**
 * Concrete controller implementing the bounded-generic interface.
 * It does NOT re-declare any mapping annotations — they should all be
 * inherited from IController.
 */
@RestController
public class BusinessController implements IController<ConcreteQuery> {

    @Override
    public Result<String> query(ConcreteQuery query) {
        return Result.success("ok");
    }

    @Override
    public Result<String> save(ConcreteQuery query) {
        return Result.success("ok");
    }

    @Override
    public Result<String> delete(Long id) {
        return Result.success("ok");
    }
}
