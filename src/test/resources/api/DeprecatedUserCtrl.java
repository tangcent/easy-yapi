package com.itangcent.api;

import com.itangcent.annotation.MyDeprecated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/user/deprecated")
public class DeprecatedUserCtrl {

    /**
     * Say hello
     *
     * @return Returns a simple greeting message
     * @deprecated This method is deprecated, not recommended for continued use
     */
    @MyDeprecated
    @RequestMapping(value = "/index")
    public String greeting() {
        return "hello world";
    }
}
