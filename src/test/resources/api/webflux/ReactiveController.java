package com.itangcent.webflux;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.reactivestreams.Publisher;
import com.itangcent.model.UserInfo;

@RestController
@RequestMapping("/reactive")
public class ReactiveController {

    @GetMapping("/user/{id}")
    public Mono<UserInfo> getUser(@PathVariable Long id) {
        return Mono.just(new UserInfo());
    }

    @GetMapping("/users")
    public Flux<UserInfo> getAllUsers() {
        return Flux.just(new UserInfo());
    }

    @PostMapping("/user")
    public Mono<UserInfo> createUser(@RequestBody Mono<UserInfo> userMono) {
        return userMono;
    }

    @GetMapping("/publisher/{id}")
    public Publisher<UserInfo> getPublisherUser(@PathVariable Long id) {
        return Mono.just(new UserInfo());
    }

    @GetMapping("/users/stream")
    public Flux<UserInfo> streamUsers() {
        return Flux.fromIterable(java.util.Collections.emptyList());
    }
}
