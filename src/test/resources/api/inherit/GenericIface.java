package com.itangcent.api.inherit;

import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

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
