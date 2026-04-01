package com.itangcent.api;


import com.itangcent.api.BaseController;
import com.itangcent.model.Default;
import com.itangcent.model.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * test about default values
 */
@RestController
@RequestMapping(value = "/default")
public class DefaultCtrl extends BaseController {

    /**
     * call with query
     */
    @GetMapping("/query")
    public Result<Default> query(Default body) {
        return Result.success(body);
    }

    /**
     * call with form
     */
    @PostMapping("/form")
    public Result<Default> form(@ModelAttribute Default form) {
        return Result.success(form);
    }

    /**
     * call with body
     */
    @PostMapping("/body")
    public Result<Default> body(@RequestBody Default body) {
        return Result.success(body);
    }
}
