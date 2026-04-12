package com.itangcent.spring.entity;

import org.springframework.http.HttpEntity;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import com.itangcent.model.UserInfo;

@RestController
@RequestMapping("/entity")
public class EntityController {

    @GetMapping("/user/{id}")
    public ResponseEntity<UserInfo> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(new UserInfo());
    }

    @PostMapping("/user")
    public ResponseEntity<UserInfo> createUser(@RequestBody UserInfo user) {
        return ResponseEntity.ok(user);
    }

    @GetMapping("/async/{id}")
    public DeferredResult<UserInfo> getAsyncUser(@PathVariable Long id) {
        DeferredResult<UserInfo> result = new DeferredResult<>();
        return result;
    }

    @PutMapping("/user")
    public HttpEntity<UserInfo> updateUser(@RequestBody UserInfo user) {
        return new HttpEntity<>(user);
    }

    @PostMapping("/request")
    public UserInfo processRequest(@RequestBody RequestEntity<UserInfo> request) {
        return request.getBody();
    }
}
