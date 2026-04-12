package com.itangcent.validation.spring;

import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/spring/validated")
public class SpringValidationController {

    @PostMapping("/user")
    public String createUser(@RequestBody SpringValidatedUserDTO user, BindingResult bindingResult) {
        return "created";
    }

    @GetMapping("/user/{id}")
    public SpringValidatedUserDTO getUser(@PathVariable Long id) {
        return new SpringValidatedUserDTO();
    }
}
