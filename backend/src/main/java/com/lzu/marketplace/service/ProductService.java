package com.lzu.marketplace.service;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.mapper.ProductMapper;
import com.lzu.marketplace.pojo.Product;
import com.lzu.marketplace.pojo.dto.ProductCreateRequest;
import com.lzu.marketplace.pojo.dto.ProductListResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品业务：发布、列表查询（筛选 + 分页）、详情、上下架。
 * 参数校验全部在这里做，校验失败抛 BusinessException(40001, 400, ...)。
 */
@Service
public class ProductService {

    private static final int TITLE_MAX = 50;
    private static final int CATEGORY_MAX = 32;
    private static final int DESCRIPTION_MAX = 500;
    private static final int SELLER_NAME_MAX = 50;

    private static final int PAGE_SIZE_MAX = 50;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 发布商品：校验 -> 落库（status 默认 on）-> 回查返回完整记录（含 id、createdAt）。
     */
    public Product create(ProductCreateRequest request) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }

        String title = trimToNull(request.getTitle());
        if (title == null) {
            throw badRequest("title 不能为空");
        }
        if (title.length() > TITLE_MAX) {
            throw badRequest("title 长度不能超过 " + TITLE_MAX);
        }

        Integer price = request.getPrice();
        if (price == null) {
            throw badRequest("price 不能为空");
        }
        if (price < 0) {
            throw badRequest("price 不能为负数");
        }

        Integer stock = request.getStock();
        if (stock == null) {
            throw badRequest("stock 不能为空");
        }
        if (stock < 0) {
            throw badRequest("stock 不能为负数");
        }

        String category = trimToNull(request.getCategory());
        if (category != null && category.length() > CATEGORY_MAX) {
            throw badRequest("category 长度不能超过 " + CATEGORY_MAX);
        }

        String description = trimToNull(request.getDescription());
        if (description != null && description.length() > DESCRIPTION_MAX) {
            throw badRequest("description 长度不能超过 " + DESCRIPTION_MAX);
        }

        String sellerName = trimToNull(request.getSellerName());
        if (sellerName != null && sellerName.length() > SELLER_NAME_MAX) {
            throw badRequest("sellerName 长度不能超过 " + SELLER_NAME_MAX);
        }

        Product product = new Product();
        product.setTitle(title);
        product.setPrice(price);
        product.setStock(stock);
        product.setCategory(category);
        product.setDescription(description);
        product.setSellerName(sellerName);
        product.setStatus("on");

        productMapper.insert(product);
        return productMapper.findById(product.getId());
    }

    /**
     * 商品列表：keyword（标题模糊）/ status / category 三个条件可叠加，分页返回。
     */
    public ProductListResponse page(String keyword, String status, String category, int page, int pageSize) {
        if (page < 1) {
            throw badRequest("page 必须大于等于 1");
        }
        if (pageSize < 1 || pageSize > PAGE_SIZE_MAX) {
            throw badRequest("pageSize 必须在 1~" + PAGE_SIZE_MAX + " 之间");
        }

        String statusFilter = trimToNull(status);
        if (statusFilter != null && !"on".equals(statusFilter) && !"off".equals(statusFilter)) {
            throw badRequest("status 只能为 on 或 off");
        }

        String keywordFilter = trimToNull(keyword);
        String categoryFilter = trimToNull(category);

        long offset = (long) (page - 1) * pageSize;
        long total = productMapper.count(keywordFilter, statusFilter, categoryFilter);
        List<Product> items = productMapper.page(keywordFilter, statusFilter, categoryFilter, offset, pageSize);
        return new ProductListResponse(total, page, pageSize, items);
    }

    /** 详情：不存在抛 404 + 40401。 */
    public Product findById(Long id) {
        Product product = (id == null) ? null : productMapper.findById(id);
        if (product == null) {
            throw new BusinessException(40401, 404, "商品不存在");
        }
        return product;
    }

    /** 上下架：status 只能是 on / off；商品不存在返回 404。 */
    public Product updateStatus(Long id, String status) {
        String value = trimToNull(status);
        if (!"on".equals(value) && !"off".equals(value)) {
            throw badRequest("status 只能为 on 或 off");
        }
        findById(id);
        productMapper.updateStatus(id, value);
        return productMapper.findById(id);
    }

    private static BusinessException badRequest(String message) {
        return new BusinessException(40001, 400, message);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
