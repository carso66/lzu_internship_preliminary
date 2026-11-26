package com.example.demo.service;

import com.example.demo.dto.UpdateProductStatusRequest;
import com.example.demo.common.BizException;
import com.example.demo.common.ErrorCode;
import com.example.demo.dto.CreateProductRequest;
import com.example.demo.model.Product;
import com.example.demo.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.demo.common.PageResult;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product create(CreateProductRequest request) {

        if (request == null) {
            throw invalid("请求体不能为空");
        }

        // 必填文本：检查非空、去除首尾空白、检查长度
        String title = requiredText(request.title(), "title", 100);
        String category = requiredText(request.category(), "category", 50);
        String sellerName = requiredText(
                request.sellerName(), "sellerName", 50
        );

        // 金额单位为分，允许为 0，不允许负数
        if (request.price() == null || request.price() < 0) {
            throw invalid("price 必须提供，且不能小于 0");
        }

        // 库存允许为 0，不允许负数
        if (request.stock() == null || request.stock() < 0) {
            throw invalid("stock 必须提供，且不能小于 0");
        }


        // 可选文本：没传时使用空字符串
        String description = optionalText(
                request.description(), "description", 2000
        );

        String imageUrl = optionalText(
                request.imageUrl(), "imageUrl", 500
        );

        // record 不可修改，因此创建一份处理后的请求对象
        CreateProductRequest normalizedRequest =
                new CreateProductRequest(
                        title,
                        request.price(),
                        request.stock(),
                        category,
                        description,
                        sellerName,
                        imageUrl
                );

        // 插入数据库，取得生成的 ID
        long id = productRepository.insert(normalizedRequest);

        // 查回完整商品，包括数据库生成的状态和时间
        return productRepository.findById(id)
                .orElseThrow(() ->
                        new IllegalStateException("新增商品后查询失败")
                );
    }
    public PageResult<Product> list(
            String keyword,
            String status,
            String category,
            int page,
            int pageSize
    ) {
        if (page < 1) {
            throw invalid("page 必须大于等于 1");
        }

        if (pageSize < 1 || pageSize > 50) {
            throw invalid("pageSize 必须在 1 到 50 之间");
        }

        if (status != null
                && !"on".equals(status)
                && !"off".equals(status)) {
            throw invalid("status 只能是 on 或 off");
        }

        return productRepository.findPage(
                keyword, status, category, page, pageSize
        );
    }
    public Product getById(long id) {
        if (id <= 0) {
            throw invalid("id 必须是正整数");
        }

        return productRepository.findById(id)
                .orElseThrow(() ->
                        new BizException(
                                ErrorCode.NOT_FOUND,
                                "商品不存在"
                        )
                );
    }

    private String requiredText(
            String value, String fieldName, int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName + " 不能为空");
        }

        String cleaned = value.strip();

        if (cleaned.length() > maxLength) {
            throw invalid(fieldName + " 长度不能超过 " + maxLength);
        }

        return cleaned;
    }

    private String optionalText(
            String value, String fieldName, int maxLength
    ) {
        if (value == null) {
            return "";
        }

        if (value.length() > maxLength) {
            throw invalid(fieldName + " 长度不能超过 " + maxLength);
        }

        return value;
    }
    @Transactional
    public Product updateStatus(
            long id, UpdateProductStatusRequest request
    ) {
        if (id <= 0) {
            throw invalid("id 必须是正整数");
        }

        if (request == null) {
            throw invalid("请求体不能为空");
        }

        String status = request.status();

        if (!"on".equals(status) && !"off".equals(status)) {
            throw invalid("status 只能是 on 或 off");
        }

        int affectedRows = productRepository.updateStatus(id, status);

        if (affectedRows == 0) {
            throw new BizException(
                    ErrorCode.NOT_FOUND,
                    "商品不存在"
            );
        }

        return getById(id);
    }

    private BizException invalid(String message) {
        return new BizException(ErrorCode.PARAM_INVALID, message);
    }
}