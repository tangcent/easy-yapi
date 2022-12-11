package com.itangcent.springboot.demo.controller;


import com.itangcent.constant.Add;
import com.itangcent.constant.Update;
import com.itangcent.model.ValidationDemoDto;
import com.itangcent.model.ValidationGroupedDemoDto;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


/**
 * Validation API
 */
@RestController
@RequestMapping(value = "/test/validation")
public class ValidationCtrl {

    /**
     * demo
     */
    @PostMapping("/demo")
    public ValidationDemoDto demo(
            @RequestBody ValidationDemoDto validationDemoDto) {
        return validationDemoDto;
    }

    /**
     * demo-add
     */
    @PostMapping("/demo/add")
    public void demoAdd(
            @Validated(Add.class) @RequestBody ValidationGroupedDemoDto validationGroupedDemoDto) {
        return;
    }

    /**
     * demo-update
     */
    @PostMapping("/demo/update")
    public void demoUpdate(
            @Validated(Update.class) @RequestBody ValidationGroupedDemoDto validationGroupedDemoDto) {
        return;
    }

    /**
     * demo-no-group
     */
    @PostMapping("/demo/nogroup")
    public void demoNoGroup(
            @Validated @RequestBody ValidationGroupedDemoDto validationGroupedDemoDto) {
        return;
    }

}
