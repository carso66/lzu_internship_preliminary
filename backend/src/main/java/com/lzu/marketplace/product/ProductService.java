package com.lzu.marketplace.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.lzu.marketplace.common.BusinessException;

/** 商品业务入口，负责划定事务范围，让控制器不直接操作数据库。 */
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    // 插入和读取结果在同一事务内；数据库操作抛出运行时异常时回滚本次写入。
    @Transactional
    public Product create(CreateProductRequest request) {
        return repository.create(request);
    }

    // 列表只读，标记只读事务，避免开启不必要的写事务。
    @Transactional(readOnly = true)
    public ProductPage list(ProductQuery query) {
        return repository.list(query);
    }

    @Transactional(readOnly = true)
    public Product detail(long id) {
        checkId(id);
        return repository.find(id, false).orElseThrow(() -> BusinessException.notFound("商品不存在"));
    }

    @Transactional
    public Product updateStatus(long id, String status) {
        checkId(id);
        repository.find(id, true).orElseThrow(() -> BusinessException.notFound("商品不存在"));
        repository.updateStatus(id, status);
        return repository.find(id, false).orElseThrow(() -> BusinessException.notFound("商品不存在"));
    }

    private void checkId(long id) {
        if (id < 1) throw BusinessException.badRequest("id必须为正整数");
    }
}
