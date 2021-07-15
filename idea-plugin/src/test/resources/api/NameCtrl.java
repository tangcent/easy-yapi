package com.itangcent.api;

import com.itangcent.common.annotation.Public;
import org.springframework.web.bind.annotation.RequestMapping;

public class NameCtrl extends BaseController {

    @RequestMapping(value = "/nothing")
    public String nothing() {
        return "nothing";
    }

    /**
     * say hello
     */
    @Public
    @RequestMapping(value = "/greeting")
    public String oneLine() {
        return "hello world";
    }

    /**
     * say hello
     * not update anything
     * just say hello
     */
    @Public
    @RequestMapping(value = "/greeting")
    public String muiltLine() {
        return "hello world";
    }

    /**
     * not update anything
     * just say hello
     *
     * @name say hello
     */
    @Public
    @RequestMapping(value = "/greeting")
    public String muiltLine() {
        return "hello world";
    }
}