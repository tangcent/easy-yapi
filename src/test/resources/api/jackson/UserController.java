package com.itangcent.jackson;

import com.itangcent.jackson.UserDTO;
import com.itangcent.jackson.OrderedDTO;
import com.itangcent.jackson.IgnorePropertiesDTO;
import com.itangcent.jackson.UnwrappedDTO;
import com.itangcent.jackson.ViewDTO;
import com.itangcent.jackson.JsonViewViews;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import com.fasterxml.jackson.annotation.JsonView;

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

    @PostMapping("/ordered")
    public OrderedDTO createOrdered(@RequestBody OrderedDTO dto) {
        return dto;
    }

    @PostMapping("/ignore-properties")
    public IgnorePropertiesDTO createIgnoreProperties(@RequestBody IgnorePropertiesDTO dto) {
        return dto;
    }

    @PostMapping("/unwrapped")
    public UnwrappedDTO createUnwrapped(@RequestBody UnwrappedDTO dto) {
        return dto;
    }

    @JsonView(JsonViewViews.Public.class)
    @GetMapping("/view/public/{id}")
    public ViewDTO getPublicView(@PathVariable("id") Long id) {
        return new ViewDTO();
    }

    @JsonView(JsonViewViews.Internal.class)
    @GetMapping("/view/internal/{id}")
    public ViewDTO getInternalView(@PathVariable("id") Long id) {
        return new ViewDTO();
    }

    @JsonView(JsonViewViews.Admin.class)
    @GetMapping("/view/admin/{id}")
    public ViewDTO getAdminView(@PathVariable("id") Long id) {
        return new ViewDTO();
    }
}
