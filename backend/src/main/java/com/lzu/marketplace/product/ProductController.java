package com.lzu.marketplace.product;

import com.lzu.marketplace.common.ApiResponse;
import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.product.dto.CreateProductRequest;
import com.lzu.marketplace.product.dto.ProductQuery;
import com.lzu.marketplace.product.dto.ProductResponse;
import com.lzu.marketplace.product.dto.ProductStatusResponse;
import com.lzu.marketplace.product.dto.UpdateProductStatusRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口。已实现官方 §4 L0-2 发布、L0-3 列表、L0-4 详情、L0-5 上下架。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    /**
     * POST /api/products —— 发布商品。
     *
     * <p>成功返回 HTTP 201 与完整商品对象（官方 §4 L0-2）。
     * 参数不合法由 {@code GlobalExceptionHandler} 统一转为 400 + 40001。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.ok(service.create(request));
    }

    /**
     * GET /api/products —— 商品列表（官方 §4 L0-3）。
     *
     * <p>三个筛选条件（keyword / status / category）均可省略且可叠加；
     * page 默认 1、pageSize 默认 10、pageSize 范围 1~50。
     *
     * <p>参数刻意声明为包装类型 {@code Integer}：省略时得到 null，才能区分
     * 「没传 → 用默认值」与「传了 0 / 负数 → 400」。若用原始 int，
     * 缺省值会变成 0，无法区分这两种情况。
     */
    @GetMapping
    public ApiResponse<PageResult<ProductResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {

        ProductQuery query = ProductQuery.of(keyword, status, category, page, pageSize);
        return ApiResponse.ok(service.list(query));
    }

    /**
     * GET /api/products/{id} —— 商品详情（官方 §4 L0-4）。
     *
     * <p>不存在 → 404 + 40401。路径参数非数字时由 {@code MethodArgumentTypeMismatchException}
     * 统一转成 400 + 40001（见 GlobalExceptionHandler）。
     */
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> detail(@PathVariable long id) {
        return ApiResponse.ok(service.getById(id));
    }

    /**
     * PATCH /api/products/{id}/status —— 上下架（官方 §4 L0-5）。
     *
     * <p>成功 200，data 只含 {@code id} 与 {@code status}（照题目示例）。
     * 非法状态 → 400 + 40001；商品不存在 → 404 + 40401。
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<ProductStatusResponse> updateStatus(@PathVariable long id,
                                                           @Valid @RequestBody UpdateProductStatusRequest request) {
        return ApiResponse.ok(service.updateStatus(id, request.status()));
    }
}
