package com.itangcent.validation.javax.strict;

import com.itangcent.validation.javax.strict.JavaxStrictUserDTO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/javax/strict")
public class JavaxStrictValidationController {

    @PostMapping("/user")
    public JavaxStrictUserDTO createUser(@Validated @RequestBody JavaxStrictUserDTO user) {
        return user;
    }

    @PostMapping("/create-group")
    public JavaxStrictUserDTO createWithGroup(
            @Validated(CreateGroup.class) @RequestBody JavaxStrictUserDTO user) {
        return user;
    }

    @GetMapping("/user/{id}")
    public JavaxStrictUserDTO getUser() {
        return new JavaxStrictUserDTO();
    }
}
