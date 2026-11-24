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
 * 商品接口（L0）：
 * POST /api/products、GET /api/products、GET /api/products/{id}、
 * PATCH /api/products/{id}/status。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /** L0-2 发布商品：成功返回 201 + code 0 */
    @PostMapping
    public ResponseEntity<ApiResponse<ProductResponse>> create(
            @Valid @RequestBody ProductCreateRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** L0-3 商品列表：keyword/status/category 组合筛选 + 分页 */
    @GetMapping
    public ApiResponse<ProductPageResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false)
            @Pattern(regexp = "on|off", message = "status 只能是 on 或 off") String status,
            @RequestParam(required = false) String category,
            // 注意：题目未对 page 设上限，大 page 应返回空列表(200)而非报错，
            // 因此这里只校验下界；offset 溢出已由 Service 层用 long 计算兜底。
            @RequestParam(defaultValue = "1")
            @Min(value = 1, message = "page 从 1 开始") Integer page,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "pageSize 不能小于 1")
            @Max(value = 50, message = "pageSize 最大为 50") Integer pageSize) {
        return ApiResponse.ok(productService.list(page, pageSize, keyword, status, category));
    }

    /** L0-4 商品详情：不存在 → 404 + 40401（由 Service 抛 BusinessException） */
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(productService.getById(id));
    }

    /** L0-5 上下架：status 仅 on/off，否则 400 + 40001 */
    @PatchMapping("/{id}/status")
    public ApiResponse<ProductStatusResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody ProductStatusRequest request) {
        return ApiResponse.ok(productService.updateStatus(id, request));
    }
}
