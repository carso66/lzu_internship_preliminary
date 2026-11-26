package com.campus.marketplace.service;

import com.campus.marketplace.common.BusinessException;
import com.campus.marketplace.dto.CreateProductRequest;
import com.campus.marketplace.dto.PageResult;
import com.campus.marketplace.dto.UpdateStatusResponse;
import com.campus.marketplace.model.Product;
import com.campus.marketplace.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class ProductService {

    /** 合法：只有 0-9，不允许 + - 空格 小数点 字母 */
    private static final Pattern PURE_DIGITS = Pattern.compile("\\d+");
    /** 形如 -123：用于区分"负数"与"格式错误"，给出更准确的 message */
    private static final Pattern NEGATIVE_DIGITS = Pattern.compile("-\\d+");

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product create(CreateProductRequest request) {
        if (request == null) {
            throw new BusinessException(40001, "请求体不能为空");
        }

        // 1. title：必填 + 长度
        String title = trim(request.getTitle());
        if (title == null || title.isEmpty()) {
            throw new BusinessException(40001, "title 不能为空");
        }
        if (title.length() > 128) {
            throw new BusinessException(40001, "title 长度不能超过 128");
        }

        // 2. price：必填、纯整数、>= 0
        int price = parsePrice(request.getPrice());

        // 3. stock：可选（null/空 -> 0）、纯整数、>= 0
        int stock = parseStock(request.getStock());

        // 4. category：可选 + 长度
        String category = trimToNull(request.getCategory());
        if (category != null && category.length() > 64) {
            throw new BusinessException(40001, "category 长度不能超过 64");
        }

        // 5. description：可选 + 长度
        String description = trimToNull(request.getDescription());
        if (description != null && description.length() > 500) {
            throw new BusinessException(40001, "description 长度不能超过 500");
        }

        // 6. sellerName：可选 + 长度
        String sellerName = trimToNull(request.getSellerName());
        if (sellerName != null && sellerName.length() > 64) {
            throw new BusinessException(40001, "sellerName 长度不能超过 64");
        }

        // 组装：id / createdAt 不设，status 显式 on
        Product product = new Product();
        product.setTitle(title);
        product.setPrice(price);
        product.setStock(stock);
        product.setCategory(category);
        product.setDescription(description);
        product.setSellerName(sellerName);
        product.setStatus("on");

        Long id = productRepository.insert(product);
        Product saved = productRepository.findById(id);
        if (saved == null) {
            throw new BusinessException(50000, "商品保存后读取失败");
        }
        return saved;
    }

    /** price：必填 / 纯整数 / >=0 / 不超出 int 范围 */
    private int parsePrice(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BusinessException(40001, "price 不能为空");
        }
        if (NEGATIVE_DIGITS.matcher(raw).matches()) {
            throw new BusinessException(40001, "price 必须是大于等于 0 的整数");
        }
        if (!PURE_DIGITS.matcher(raw).matches()) {
            throw new BusinessException(40001, "price 必须是纯整数，不能包含字母或小数");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new BusinessException(40001, "price 数值超出范围");
        }
    }

    /** stock：可选 / 纯整数 / >=0 / 不超出 int 范围 */
    private int parseStock(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return 0;
        }
        if (NEGATIVE_DIGITS.matcher(raw).matches()) {
            throw new BusinessException(40001, "stock 必须是大于等于 0 的整数");
        }
        if (!PURE_DIGITS.matcher(raw).matches()) {
            throw new BusinessException(40001, "stock 必须是纯整数，不能包含字母或小数");
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new BusinessException(40001, "stock 数值超出范围");
        }
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    /** trim 后为空 -> null（可选字段统一存 NULL 而不是空串） */
    private String trimToNull(String s) {
        String t = trim(s);
        return (t == null || t.isEmpty()) ? null : t;
    }

    // ===== L0-3 新增：商品列表 =====

    public PageResult<Product> list(String keyword, String status, String category,
                                    String page, String pageSize) {
        String normalizedKeyword = trimToNull(keyword);
        String normalizedStatus = trimToNull(status);
        String normalizedCategory = trimToNull(category);

        if (normalizedStatus != null
                && !"on".equals(normalizedStatus)
                && !"off".equals(normalizedStatus)) {
            throw new BusinessException(40001, "status 只能是 on 或 off");
        }

        int pageNumber = parsePage(page);
        int size = parsePageSize(pageSize);

        long total = productRepository.count(normalizedKeyword, normalizedStatus, normalizedCategory);

        List<Product> items;
        long offsetLong = (long) (pageNumber - 1) * size;
        if (total == 0 || offsetLong > Integer.MAX_VALUE) {
            items = new ArrayList<>();
        } else {
            items = productRepository.findPage(normalizedKeyword, normalizedStatus,
                    normalizedCategory, (int) offsetLong, size);
        }

        return new PageResult<>(total, pageNumber, size, items);
    }

    /** L0-4：商品详情 */
    public Product getById(String id) {
        if (id == null || !id.matches("\\d+")) {
            throw new BusinessException(40001, "id 必须是大于 0 的整数");
        }

        long productId;
        try {
            productId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BusinessException(40001, "id 数值超出范围");
        }

        if (productId < 1L) {
            throw new BusinessException(40001, "id 必须是大于 0 的整数");
        }

        Product product = productRepository.findById(productId);
        if (product == null) {
            throw new BusinessException(40401, "商品不存在");
        }
        return product;
    }

    /** L0-5：商品上下架 */
    public UpdateStatusResponse updateStatus(String id, String status) {
        if (id == null || !id.matches("\\d+")) {
            throw new BusinessException(40001, "id 必须是大于 0 的整数");
        }

        long productId;
        try {
            productId = Long.parseLong(id);
        } catch (NumberFormatException e) {
            throw new BusinessException(40001, "id 数值超出范围");
        }

        if (productId < 1L) {
            throw new BusinessException(40001, "id 必须是大于 0 的整数");
        }

        if (status == null || status.trim().isEmpty()) {
            throw new BusinessException(40001, "status 不能为空");
        }

        String normalizedStatus = status.trim();
        if (!"on".equals(normalizedStatus) && !"off".equals(normalizedStatus)) {
            throw new BusinessException(40001, "status 只能是 on 或 off");
        }

        Product product = productRepository.findById(productId);
        if (product == null) {
            throw new BusinessException(40401, "商品不存在");
        }

        productRepository.updateStatus(productId, normalizedStatus);
        return new UpdateStatusResponse(productId, normalizedStatus);
    }

    private int parsePage(String raw) {
        String value = trim(raw);
        if (value == null || value.isEmpty()) {
            return 1;
        }
        if (!PURE_DIGITS.matcher(value).matches()) {
            throw new BusinessException(40001, "page 必须是大于等于 1 的整数");
        }
        int page;
        try {
            page = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(40001, "page 数值超出范围");
        }
        if (page < 1) {
            throw new BusinessException(40001, "page 必须是大于等于 1 的整数");
        }
        return page;
    }

    private int parsePageSize(String raw) {
        String value = trim(raw);
        if (value == null || value.isEmpty()) {
            return 10;
        }
        if (!PURE_DIGITS.matcher(value).matches()) {
            throw new BusinessException(40001, "pageSize 必须是 1~50 之间的整数");
        }
        int size;
        try {
            size = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new BusinessException(40001, "pageSize 必须是 1~50 之间的整数");
        }
        if (size < 1 || size > 50) {
            throw new BusinessException(40001, "pageSize 必须是 1~50 之间的整数");
        }
        return size;
    }
}
