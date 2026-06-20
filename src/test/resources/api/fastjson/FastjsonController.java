package com.itangcent.fastjson;

import com.itangcent.fastjson.FastjsonDTO;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fastjson")
public class FastjsonController {

    @PostMapping("/create")
    public FastjsonDTO create(@RequestBody FastjsonDTO dto) {
        return dto;
    }
}
