package com.itangcent.gson;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/product")
public class ProductController {

    @PostMapping("/create")
    public ProductDTO createProduct(@RequestBody ProductDTO product) {
        return product;
    }

    @GetMapping("/get/{id}")
    public ProductDTO getProduct(@PathVariable("id") Long id) {
        return new ProductDTO();
    }
}
