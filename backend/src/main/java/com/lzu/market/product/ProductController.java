package com.lzu.market.product;

import com.lzu.market.common.ApiException;
import com.lzu.market.common.ApiResponse;
import com.lzu.market.common.PageResult;
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

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductDao productDao;

    public ProductController(ProductDao productDao) {
        this.productDao = productDao;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Product> create(@Valid @RequestBody CreateProductReq req) {
        return ApiResponse.ok(productDao.create(req));
    }

    @GetMapping
    public ApiResponse<PageResult<Product>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        if (page < 1) {
            throw ApiException.badRequest("page 必须 ≥ 1");
        }
        if (pageSize < 1 || pageSize > 50) {
            throw ApiException.badRequest("pageSize 必须在 1~50 之间");
        }
        if (status != null && !status.isBlank() && !"on".equals(status) && !"off".equals(status)) {
            throw ApiException.badRequest("status 只能是 on 或 off");
        }
        return ApiResponse.ok(productDao.findPage(keyword, status, category, page, pageSize));
    }

    @GetMapping("/{id}")
    public ApiResponse<Product> detail(@PathVariable long id) {
        Product p = productDao.findById(id)
                .orElseThrow(() -> ApiException.notFound("商品不存在"));
        return ApiResponse.ok(p);
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<ProductStatusView> updateStatus(@PathVariable long id,
                                                      @Valid @RequestBody UpdateStatusReq req) {
        int affected = productDao.updateStatus(id, req.status());
        if (affected == 0) {
            throw ApiException.notFound("商品不存在");
        }
        return ApiResponse.ok(new ProductStatusView(id, req.status()));
    }
}
