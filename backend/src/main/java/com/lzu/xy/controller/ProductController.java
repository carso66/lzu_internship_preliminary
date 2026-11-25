package com.lzu.xy.controller;

import com.lzu.xy.controller.model.dto.ProductCreateDTO;
import com.lzu.xy.controller.model.dto.ProductQueryDTO;
import com.lzu.xy.controller.model.dto.ProductStatusDTO;
import com.lzu.xy.response.Result;
import com.lzu.xy.service.ProductService;
import com.lzu.xy.controller.model.vo.PageVO;
import com.lzu.xy.controller.model.vo.ProductStatusVO;
import com.lzu.xy.controller.model.vo.ProductVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Result<ProductVO> create(@Valid @RequestBody ProductCreateDTO dto) {
        return Result.success(productService.create(dto));
    }

    @GetMapping
    public Result<PageVO<ProductVO>> list(@Valid ProductQueryDTO query) {
        return Result.success(productService.list(query));
    }

    @GetMapping("/{id}")
    public Result<ProductVO> detail(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @PatchMapping("/{id}/status")
    public Result<ProductStatusVO> updateStatus(@PathVariable Long id,
                                                @Valid @RequestBody ProductStatusDTO dto) {
        return Result.success(productService.updateStatus(id, dto));
    }
}
