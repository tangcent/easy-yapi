package com.itangcent.mybatisplus;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mybatisplus")
public class UserController {

    @PostMapping("/user/create")
    public UserDTO create(@RequestBody UserDTO dto) {
        return dto;
    }

    @GetMapping("/user/get")
    public UserDTO get() {
        return new UserDTO();
    }
}
