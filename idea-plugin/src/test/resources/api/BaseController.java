package com.itangcent.api;

import org.springframework.web.bind.annotation.RequestMapping;

public class BaseController {

    /**
     * current ctrl name
     *
     * @public
     */
    @RequestMapping("/ctrl/name")
    public String ctrlName() {
        return getClass().getName();
    }

}
