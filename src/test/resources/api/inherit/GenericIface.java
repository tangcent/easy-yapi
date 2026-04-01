package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.*;

/**
 * Case 4: generic interface with mapping annotations.
 */
@RequestMapping("/generic-iface")
public interface GenericIface<Req, Res> {

    /**
     * query item
     */
    @GetMapping("/query")
    Result<Res> query();

    /**
     * save item
     */
    @PostMapping("/save")
    Result<Res> save(@RequestBody Req input);
}
