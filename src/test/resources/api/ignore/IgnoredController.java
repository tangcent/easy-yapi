package com.itangcent.ignore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * @ignore
 */
@RestController
@RequestMapping("/ignored")
public class IgnoredController {

    @GetMapping("/method")
    public String method() {
        return "should not be exported";
    }
}
