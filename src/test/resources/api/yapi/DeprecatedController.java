package com.itangcent.yapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Deprecated
@RestController
@RequestMapping("/deprecated")
public class DeprecatedController {

    /**
     * Old method
     *
     * @return Returns old data
     */
    @GetMapping("/old")
    public String oldMethod() {
        return "old data";
    }
}
