package com.itangcent.ignore;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/normal")
public class NormalController {

    /**
     * Normal method
     *
     * @return Returns normal data
     */
    @GetMapping("/method")
    public String method() {
        return "normal data";
    }

    /**
     * @ignore
     */
    @GetMapping("/ignored-method")
    public String ignoredMethod() {
        return "should not be exported";
    }
}
