package com.zyh.marketplace.service;

import com.zyh.marketplace.common.PageResult;
import com.zyh.marketplace.common.ProductStatus;
import com.zyh.marketplace.dto.CreateProductRequest;
import com.zyh.marketplace.dto.ProductPageCriteria;
import com.zyh.marketplace.dto.ProductQuery;
import com.zyh.marketplace.dto.ProductResponse;
import com.zyh.marketplace.dto.ProductStatusResponse;
import com.zyh.marketplace.dto.UpdateProductStatusRequest;
import com.zyh.marketplace.entity.Product;
import com.zyh.marketplace.exception.BusinessException;
import com.zyh.marketplace.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 商品业务：参数校验 + 业务规则 + 事务边界。
 *
 * <p>校验思路：支撑层的解析器已挡掉「JSON 类型错误」（小数 / 字符串 / 布尔直接 40001），
 * 这里再做「必填、长度、范围、枚举」校验，错误文案精确到字段名。
 * 长度上限全部对齐 schema.sql 的列宽，保证「通过校验的输入一定不会撞库层约束」。
 */
@Service
public class ProductService {

    /** 与 products.title VARCHAR(100) 对应 */
    private static final int TITLE_MAX = 100;
    /** 与 products.category VARCHAR(32) 对应 */
    private static final int CATEGORY_MAX = 32;
    /** 与 products.description VARCHAR(1000) 对应 */
    private static final int DESCRIPTION_MAX = 1000;
    /** 与 products.seller_name VARCHAR(64) 对应 */
    private static final int SELLER_NAME_MAX = 64;
    /** 与 products.stock INT 对应 */
    private static final long STOCK_MAX = Integer.MAX_VALUE;
    /** price 是 BIGINT，这里再加一个业务上限，避免把超大值写进库 */
    private static final long PRICE_MAX = 999_999_999L;

    private static final int PAGE_SIZE_MAX = 50;
    /** 页码上限：与 offset 的 int 范围配合，防止分页偏移量溢出 */
    private static final long PAGE_MAX = 10_000_000L;

    /** 纯整数文本：可带正负号，不允许小数点 / 科学计数 */
    private static final Pattern INTEGER_TEXT = Pattern.compile("-?\\d{1,19}");
    /** LIKE 通配符，用于 keyword 转义 */
    private static final Pattern LIKE_SPECIAL = Pattern.compile("([\\\\%_])");

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ProductMapper productMapper;

    public ProductService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    // ------------------------------------------------------------------ L0-2

    @Transactional
    public ProductResponse create(CreateProductRequest request) {
        rejectUnknownField(request.getUnknownField(), "title, price, stock, category, description, sellerName");

        String title = requiredText("title", request.getTitle(), TITLE_MAX);
        long price = requiredLong("price", request.getPrice(), 1L, PRICE_MAX);
        long stock = requiredLong("stock", request.getStock(), 0L, STOCK_MAX);
        String category = requiredText("category", request.getCategory(), CATEGORY_MAX);
        String sellerName = requiredText("sellerName", request.getSellerName(), SELLER_NAME_MAX);
        String description = optionalText("description", request.getDescription(), DESCRIPTION_MAX);

        Product product = new Product();
        product.setTitle(title);
        product.setPrice(price);
        product.setStock((int) stock);
        product.setCategory(category);
        product.setDescription(description);
        // 新建商品一律上架
        product.setStatus(ProductStatus.ON);
        product.setSellerName(sellerName);

        productMapper.insert(product);

        // 重新读一次，createdAt 以数据库默认值 CURRENT_TIMESTAMP 为准
        Product saved = productMapper.findById(product.getId());
        if (saved == null) {
            throw new IllegalStateException("商品写入后无法读回，id=" + product.getId());
        }
        return toResponse(saved);
    }

    // ------------------------------------------------------------------ L0-3

    @Transactional(readOnly = true)
    public PageResult<ProductResponse> list(ProductQuery query) {
        String keyword = normalizeKeyword(query.keyword());
        String status = normalizeStatus(query.status());
        String category = normalizeCategory(query.category());
        int page = positiveInt("page", query.page(), 1L, PAGE_MAX);
        int pageSize = positiveInt("pageSize", query.pageSize(), 1L, PAGE_SIZE_MAX);

        // offset 先用 long 计算再判断是否超出 int 范围，避免 int 乘法溢出成负数
        long offset = (long) (page - 1) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw BusinessException.badRequest("字段 page 过大：page * pageSize 超出的分页偏移量上限");
        }

        ProductPageCriteria criteria = new ProductPageCriteria(keyword, status, category, (int) offset, pageSize);
        long total = productMapper.countByCriteria(criteria);
        List<Product> products = productMapper.findByCriteria(criteria);
        List<ProductResponse> items = products.stream().map(ProductService::toResponse).toList();
        return new PageResult<>(total, page, pageSize, items);
    }

    // ------------------------------------------------------------------ L0-4

    @Transactional(readOnly = true)
    public ProductResponse detail(Long id) {
        Product product = productMapper.findById(id);
        if (product == null) {
            throw BusinessException.notFound("商品不存在：id=" + id);
        }
        return toResponse(product);
    }

    // ------------------------------------------------------------------ L0-5

    @Transactional
    public ProductStatusResponse updateStatus(Long id, UpdateProductStatusRequest request) {
        rejectUnknownField(request.getUnknownField(), "status");

        String status = request.getStatus();
        if (status == null || status.isBlank()) {
            throw BusinessException.badRequest("字段 status 不能为空，只能是 on 或 off");
        }
        if (!ProductStatus.isValid(status)) {
            throw BusinessException.badRequest("字段 status 非法：只能是 on 或 off");
        }

        int affected = productMapper.updateStatus(id, status);
        if (affected == 0) {
            throw BusinessException.notFound("商品不存在：id=" + id);
        }
        return new ProductStatusResponse(id, status);
    }

    // ------------------------------------------------------------------ 校验细节

    private void rejectUnknownField(String unknownField, String allowed) {
        if (unknownField != null) {
            throw BusinessException.badRequest("字段 " + unknownField + " 不可识别，允许的字段：" + allowed);
        }
    }

    private String requiredText(String field, String value, int maxLength) {
        if (value == null) {
            throw BusinessException.badRequest("字段 " + field + " 不能为空");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw BusinessException.badRequest("字段 " + field + " 不能为空");
        }
        if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            throw BusinessException.badRequest("字段 " + field + " 长度不能超过 " + maxLength + " 个字符");
        }
        return trimmed;
    }

    private String optionalText(String field, String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > maxLength) {
            throw BusinessException.badRequest("字段 " + field + " 长度不能超过 " + maxLength + " 个字符");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    private long requiredLong(String field, String text, long min, long max) {
        if (text == null) {
            throw BusinessException.badRequest("字段 " + field + " 不能为空");
        }
        if (!INTEGER_TEXT.matcher(text).matches()) {
            throw BusinessException.badRequest("字段 " + field + " 必须是整数");
        }
        long value;
        try {
            value = Long.parseLong(text);
        } catch (NumberFormatException e) {
            throw BusinessException.badRequest("字段 " + field + " 超出整数范围（" + min + " ~ " + max + "）");
        }
        if (value < min) {
            throw BusinessException.badRequest("字段 " + field + " 不能小于 " + min);
        }
        if (value > max) {
            throw BusinessException.badRequest("字段 " + field + " 不能大于 " + max);
        }
        return value;
    }

    private int positiveInt(String field, String text, long min, long max) {
        long value = requiredLong(field, text, min, max);
        return (int) value;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        if (!ProductStatus.isValid(status)) {
            throw BusinessException.badRequest("字段 status 非法：只能是 on 或 off");
        }
        return status;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String trimmed = category.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > CATEGORY_MAX) {
            throw BusinessException.badRequest("字段 category 长度不能超过 " + CATEGORY_MAX + " 个字符");
        }
        return trimmed;
    }

    /**
     * keyword：空或纯空白视为不过滤（页面常把空关键字传成 keyword=），
     * 并对 % _ \ 做转义，配合 SQL 里的 ESCAPE '\'，使关键字只按字面量模糊匹配。
     */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.codePointCount(0, trimmed.length()) > TITLE_MAX) {
            throw BusinessException.badRequest("字段 keyword 长度不能超过 " + TITLE_MAX + " 个字符");
        }
        return LIKE_SPECIAL.matcher(trimmed).replaceAll("\\\\$1");
    }

    static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getTitle(),
                product.getPrice(),
                product.getStock(),
                product.getCategory(),
                product.getDescription(),
                product.getStatus(),
                product.getSellerName(),
                product.getCreatedAt() == null ? null : product.getCreatedAt().format(TIME_FORMAT)
        );
    }
}
