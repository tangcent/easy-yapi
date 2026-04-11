package com.itangcent.jackson;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/user")
public class UserController {

    @PostMapping("/create")
    public UserDTO createUser(@RequestBody UserDTO user) {
        return user;
    }

    @GetMapping("/get/{id}")
    public UserDTO getUser(@PathVariable("id") Long id) {
        return new UserDTO();
    }
}
