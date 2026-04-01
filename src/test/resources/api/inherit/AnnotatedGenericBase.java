package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.*;

/**
 * Composite case: generic base with annotations on methods.
 */
@RequestMapping("/generic-base")
public abstract class AnnotatedGenericBase<Req, Res> {

    /**
     * query item
     */
    @GetMapping("/query")
    public abstract Result<Res> query();

    /**
     * save item
     */
    @PostMapping("/save")
    public abstract Result<Res> save(@RequestBody Req input);
}
