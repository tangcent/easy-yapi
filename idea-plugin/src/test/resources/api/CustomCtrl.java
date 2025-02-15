package api.annotation;

import api.annotation.MyController;
import org.springframework.web.bind.annotation.RequestMapping;

@MyController
public class CustomCtrl {

    @RequestMapping(value = "/hello")
    public String hello() {
        return "Hello, World!";
    }
} 