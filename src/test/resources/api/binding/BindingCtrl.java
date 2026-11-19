package com.itangcent.api.binding;

import com.itangcent.model.Result;
import com.itangcent.model.binding.ConstructorQuery;
import com.itangcent.model.binding.LombokQuery;
import com.itangcent.model.binding.PageParam;
import com.itangcent.model.binding.PageQuery;
import com.itangcent.model.binding.RecordQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Objects filled by Spring's data binder from request parameters.
 */
@RestController
@RequestMapping("/binding")
public class BindingCtrl {

    /**
     * shared paging object
     */
    @GetMapping("/page")
    public Result<String> page(String keyword, PageParam page) {
        return Result.success("ok");
    }

    /**
     * query without annotation
     */
    @GetMapping("/query")
    public Result<String> query(PageQuery query) {
        return Result.success("ok");
    }

    /**
     * model attribute form
     */
    @PostMapping("/form")
    public Result<String> form(@ModelAttribute PageQuery query) {
        return Result.success("ok");
    }

    /**
     * json body
     */
    @PostMapping("/body")
    public Result<String> body(@RequestBody PageQuery query) {
        return Result.success("ok");
    }

    /**
     * constructor binding
     */
    @GetMapping("/constructor")
    public Result<String> constructor(ConstructorQuery query) {
        return Result.success("ok");
    }

    /**
     * record binding
     */
    @GetMapping("/record")
    public Result<String> record(RecordQuery query) {
        return Result.success("ok");
    }

    /**
     * lombok setters
     */
    @GetMapping("/lombok")
    public Result<String> lombok(LombokQuery query) {
        return Result.success("ok");
    }
}
