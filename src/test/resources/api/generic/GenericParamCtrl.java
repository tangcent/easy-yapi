package com.itangcent.api.generic;

import com.itangcent.model.Result;
import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Test case for issue #1302: Generic type parameters should be correctly resolved
 * when using different parameter bindings with generic types like Result<UserInfo>
 */
@RestController
@RequestMapping("/generic-param")
public class GenericParamCtrl {

    /**
     * test with @ModelAttribute
     */
    @GetMapping("/model-attribute")
    public String testModelAttribute(@ModelAttribute Result<UserInfo> param) {
        return "success";
    }

    /**
     * test with @RequestBody
     */
    @PostMapping("/request-body")
    public String testRequestBody(@RequestBody Result<UserInfo> param) {
        return "success";
    }

    /**
     * test with @RequestParam
     */
    @GetMapping("/request-param")
    public String testRequestParam(@RequestParam Result<UserInfo> param) {
        return "success";
    }

    /**
     * test with no annotation (defaults to query param)
     */
    @GetMapping("/no-annotation")
    public String testNoAnnotation(Result<UserInfo> param) {
        return "success";
    }
}
