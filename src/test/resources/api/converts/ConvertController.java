package com.itangcent.converts;

import com.itangcent.converts.ConvertDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/converts")
public class ConvertController {

    @PostMapping("/create")
    public ConvertDTO create(@RequestBody ConvertDTO dto) {
        return dto;
    }
}
