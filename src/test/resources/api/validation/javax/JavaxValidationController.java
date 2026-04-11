package com.itangcent.validation.javax;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;

@RestController
@RequestMapping("/javax/validated")
public class JavaxValidationController {

    @PostMapping("/user")
    public JavaxValidatedUserDTO createUser(@Valid @RequestBody JavaxValidatedUserDTO user) {
        return user;
    }

    @GetMapping("/user/{id}")
    public JavaxValidatedUserDTO getUser(@NotNull @PathVariable("id") Long id) {
        return new JavaxValidatedUserDTO();
    }
}
