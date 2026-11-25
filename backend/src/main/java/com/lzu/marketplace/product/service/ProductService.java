package com.lzu.marketplace.product.service;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.product.dto.CreateProductRequest;
import com.lzu.marketplace.product.dto.UpdateStatusResult;
import com.lzu.marketplace.product.model.Product;
import com.lzu.marketplace.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 商品业务逻辑：参数兜底与校验、调用数据访问、抛出业务异常。
 */
@Service
public class ProductService {

    private static final Set<String> VALID_STATUS = Set.of("on", "off");

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Product create(CreateProductRequest request) {
        String description = (request.description() == null) ? "" : request.description().trim();
        return repository.insert(
                request.title().trim(),
                request.price(),
                request.stock(),
                request.category().trim(),
                description,
                request.sellerName().trim());
    }

    public PageResult<Product> list(String keyword, String status, String category,
                                    Integer page, Integer pageSize) {
        int p = (page == null) ? 1 : page;
        int size = (pageSize == null) ? 10 : pageSize;
        if (p < 1) {
            throw BusinessException.badRequest("page 必须大于等于 1");
        }
        if (size < 1 || size > 50) {
            throw BusinessException.badRequest("pageSize 必须在 1 到 50 之间");
        }

        String kw = normalize(keyword);
        String st = normalize(status);
        String ct = normalize(category);
        if (st != null && !VALID_STATUS.contains(st)) {
            throw BusinessException.badRequest("status 只能是 on 或 off");
        }

        // offset 用 long 计算，page 很大时也不会发生 int 溢出
        long offset = (long) (p - 1) * size;
        return repository.findPage(kw, st, ct, p, size, offset);
    }

    public Product getById(long id) {
        Product product = repository.findById(id);
        if (product == null) {
            throw BusinessException.notFound("商品不存在");
        }
        return product;
    }

    public UpdateStatusResult updateStatus(long id, String status) {
        if (status == null || status.isBlank()) {
            throw BusinessException.badRequest("status 不能为空");
        }
        if (!VALID_STATUS.contains(status)) {
            throw BusinessException.badRequest("status 只能是 on 或 off");
        }
        int rows = repository.updateStatus(id, status);
        if (rows == 0) {
            throw BusinessException.notFound("商品不存在");
        }
        return new UpdateStatusResult(id, status);
    }

    /** 空白字符串统一视作"未传"，返回 null；非空白则去除首尾空格。 */
    private static String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
