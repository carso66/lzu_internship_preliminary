package cn.edu.lzu.marketplace.controller;

import cn.edu.lzu.marketplace.common.ApiResponse;
import cn.edu.lzu.marketplace.dto.ProductCreateRequest;
import cn.edu.lzu.marketplace.dto.ProductPageResponse;
import cn.edu.lzu.marketplace.dto.ProductResponse;
import cn.edu.lzu.marketplace.dto.ProductStatusRequest;
import cn.edu.lzu.marketplace.dto.ProductStatusResponse;
import cn.edu.lzu.marketplace.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品 HTTP 接口，负责请求绑定、参数校验和响应封装。
 * 商品发布、查询和状态更新委托给 ProductService。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    // 通过构造器注入商品服务，控制器只依赖业务接口。
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** 发布商品：校验创建请求，交由业务层创建商品，成功返回 HTTP 201。 */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody ProductCreateRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** 商品列表：接收关键字、状态、分类与分页参数，按相同条件返回总数和当前页。 */
    @GetMapping
    public ApiResponse<ProductPageResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @Pattern(regexp = "on|off", message = "status 只能是 on 或 off") String status,
            @RequestParam(required = false) String category,
            // 这里只校验页码下界。超出数据范围的合法页码可返回空列表，偏移量由业务层按 long 计算。
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page 从 1 开始") Integer page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 不能小于 1")
            @Max(value = 50, message = "pageSize 最大为 50") Integer pageSize) {
        return ApiResponse.ok(productService.list(page, pageSize, keyword, status, category));
    }

    /** 商品详情：按商品 ID 查询，不存在时返回 HTTP 404 和业务码 40401。 */
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    /** 上下架：校验请求状态仅为 on 或 off，并返回更新后的状态。 */
    @PatchMapping("/{id}/status")
    public ApiResponse<ProductStatusResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusRequest request) {
        return ApiResponse.ok(productService.updateStatus(id, request));
    }
}
