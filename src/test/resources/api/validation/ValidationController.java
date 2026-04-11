package com.itangcent.validation;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/validated")
public class ValidationController {

    @PostMapping("/user")
    public ValidatedUserDTO createUser(@Valid @RequestBody ValidatedUserDTO user) {
        return user;
    }

    @GetMapping("/user/{id}")
    public ValidatedUserDTO getUser(@NotNull @PathVariable("id") Long id) {
        return new ValidatedUserDTO();
    }
}
