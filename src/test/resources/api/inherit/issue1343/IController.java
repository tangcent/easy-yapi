package com.itangcent.api.inherit.issue1343;

import com.itangcent.api.inherit.issue1343.IQuery;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Parent interface with bounded generic, declaring all the mapping annotations.
 * Mirrors the screenshot from issue #1343 (IController<LQ extends IQuery>).
 */
@RequestMapping("/api")
public interface IController<LQ extends IQuery> {

    /**
     * query
     */
    @GetMapping("/query")
    Result<String> query(LQ query);

    /**
     * save
     */
    @PostMapping("/save")
    Result<String> save(@RequestBody LQ query);

    /**
     * delete by id
     */
    @DeleteMapping("/{id}")
    Result<String> delete(@PathVariable("id") Long id);
}
