package com.itangcent.swagger;

import com.itangcent.swagger.ProductDTO;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
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

    @ApiOperation(value = "Search products", tags = {"product", "search"})
    @GetMapping("/search")
    public java.util.List<ProductDTO> searchProducts(
            @ApiParam(value = "keyword") String keyword) {
        return java.util.Collections.emptyList();
    }

    @GetMapping("/list")
    public java.util.List<ProductDTO> listProducts(
            @ApiParam(value = "page number", defaultValue = "1") Integer page,
            @ApiParam(value = "page size", defaultValue = "20") Integer size,
            @ApiParam(value = "filter", hidden = true) String filter) {
        return java.util.Collections.emptyList();
    }

    // @ApiImplicitParams on the METHOD (not on an argument) carries parameters
    // that have no matching Java signature. The `export.after[...]` rules above
    // push them onto the endpoint via `api.setHeader(...)` / `api.setParam(...)`.
    @ApiImplicitParams({
            @ApiImplicitParam(paramType = "header", name = "Authorization", value = "token令牌", required = true),
            @ApiImplicitParam(paramType = "header", name = "tenantId", value = "租户Id", required = true),
            @ApiImplicitParam(paramType = "query", name = "traceId", value = "trace id", defaultValue = "0")
    })
    @GetMapping("/implicit")
    public java.util.List<ProductDTO> implicitParams() {
        return java.util.Collections.emptyList();
    }

    // The single-annotation form: @ApiImplicitParam used without the container.
    @ApiImplicitParam(paramType = "header", name = "X-Trace-Id", value = "trace header")
    @GetMapping("/implicit/single")
    public java.util.List<ProductDTO> implicitParam() {
        return java.util.Collections.emptyList();
    }

    // `notes` is the second most used @ApiOperation attribute and merges into
    // the same description as `value` (method.doc is MERGE_DISTINCT).
    @ApiOperation(value = "Update a product", notes = "Requires the product to exist")
    @PostMapping("/update")
    public ProductDTO updateProduct(@RequestBody ProductDTO product) {
        return product;
    }

    // `hidden` hides the operation the same way @Api#hidden hides a class.
    // The method must not appear in the exported endpoints at all.
    @ApiOperation(value = "Internal cleanup", hidden = true)
    @PostMapping("/cleanup")
    public void cleanupProduct() {
    }

    // `httpMethod` is the fallback used only when the mapping itself declares
    // no method. This @RequestMapping says nothing about the method.
    @ApiOperation(value = "Disable a product", httpMethod = "DELETE")
    @RequestMapping("/disable")
    public ProductDTO disableProduct() {
        return new ProductDTO();
    }

    // A single @ApiResponse carries the declared status code and message into
    // the endpoint description.
    @ApiOperation(value = "Delete a product")
    @ApiResponse(code = 404, message = "product not found")
    @PostMapping("/delete")
    public ProductDTO deleteProduct() {
        return new ProductDTO();
    }

    // The container form: every entry inside @ApiResponses is appended.
    @ApiOperation(value = "Reserve a product")
    @ApiResponses({
            @ApiResponse(code = 400, message = "bad request"),
            @ApiResponse(code = 409, message = "stock conflict")
    })
    @PostMapping("/reserve")
    public ProductDTO reserveProduct(@RequestBody ProductDTO product) {
        return product;
    }

    // `example` on @ApiParam feeds `param.demo`; the neighbouring `size` has
    // no example and must not gain an empty one.
    @GetMapping("/examples")
    public java.util.List<ProductDTO> examples(
            @ApiParam(value = "page number", example = "1") Integer page,
            @ApiParam(value = "page size") Integer size) {
        return java.util.Collections.emptyList();
    }
}
