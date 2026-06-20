package com.itangcent.deprecated;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/deprecated")
public class DeprecatedController {

    /**
     * Old method
     *
     * @deprecated use {@link #newMethod()} instead
     * @return Returns old data
     */
    @Deprecated
    @GetMapping("/old")
    public String oldMethod() {
        return "old data";
    }

    /**
     * New method
     *
     * @return Returns new data
     */
    @GetMapping("/new")
    public String newMethod() {
        return "new data";
    }
}
