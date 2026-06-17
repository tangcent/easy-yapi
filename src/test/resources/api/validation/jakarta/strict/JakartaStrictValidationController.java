package com.itangcent.validation.jakarta.strict;

import com.itangcent.validation.jakarta.strict.JakartaStrictUserDTO;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jakarta/strict")
public class JakartaStrictValidationController {

    @PostMapping("/user")
    public JakartaStrictUserDTO createUser(@Validated @RequestBody JakartaStrictUserDTO user) {
        return user;
    }

    @PostMapping("/create-group")
    public JakartaStrictUserDTO createWithGroup(
            @Validated(CreateGroup.class) @RequestBody JakartaStrictUserDTO user) {
        return user;
    }

    @GetMapping("/user/{id}")
    public JakartaStrictUserDTO getUser() {
        return new JakartaStrictUserDTO();
    }
}
