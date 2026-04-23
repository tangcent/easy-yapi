package com.itangcent.springboot.demo.controller;

import com.itangcent.model.UserInfo;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/body")
public class BodyParamCtrl {

    @PostMapping("/simple")
    public void simpleBody(@RequestBody String name) {
    }

    @PostMapping("/complex")
    public void complexBody(@RequestBody UserInfo user) {
    }
}
