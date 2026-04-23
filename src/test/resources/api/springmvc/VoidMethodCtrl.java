package com.itangcent.springboot.demo.controller;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/void")
public class VoidMethodCtrl {

    @GetMapping("/nothing")
    public void voidMethod() {
    }
}
