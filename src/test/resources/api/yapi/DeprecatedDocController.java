package com.itangcent.yapi;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/doc")
public class DeprecatedDocController {

    /**
     * Old method using javadoc @deprecated
     *
     * @deprecated Use {@link #newMethod()} instead
     * @return Returns old data
     */
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
