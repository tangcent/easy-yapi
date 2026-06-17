package com.itangcent.yapiproject;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/yapi-project")
public class ProjectController {

    /**
     * Get user info
     *
     * @project user-service
     * @return Returns user info
     */
    @GetMapping("/user")
    public String getUser() {
        return "user";
    }

    /**
     * Create order
     *
     * @module order-service
     * @return Created order
     */
    @PostMapping("/order")
    public String createOrder(@RequestBody String data) {
        return "order";
    }

    /**
     * No project tag
     *
     * @return Returns data
     */
    @GetMapping("/no-project")
    public String noProject() {
        return "data";
    }
}
