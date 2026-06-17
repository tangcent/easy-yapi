package com.itangcent.yapimock;

import com.itangcent.yapimock.MockDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/yapi-mock")
public class MockController {

    @PostMapping("/create")
    public MockDTO create(@RequestBody MockDTO dto) {
        return dto;
    }
}
