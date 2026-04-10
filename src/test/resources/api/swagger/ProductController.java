package com.itangcent.swagger;

import com.itangcent.swagger.ProductDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

@Api(tags = "Product Management", value = "Product API")
@RestController
@RequestMapping("/product")
public class ProductController {

    @ApiOperation(value = "Get product by ID", tags = "product")
    @GetMapping("/get/{id}")
    public ProductDTO getProduct(
            @ApiParam(value = "Product ID", required = true) @PathVariable("id") Long id) {
        return new ProductDTO();
    }

    @ApiOperation(value = "Create a new product")
    @PostMapping("/create")
    public ProductDTO createProduct(@RequestBody ProductDTO product) {
        return product;
    }

    @GetMapping("/list")
    public java.util.List<ProductDTO> listProducts(
            @ApiParam(value = "page number", defaultValue = "1") Integer page,
            @ApiParam(value = "page size", defaultValue = "20") Integer size,
            @ApiParam(value = "filter", hidden = true) String filter) {
        return java.util.Collections.emptyList();
    }
}
