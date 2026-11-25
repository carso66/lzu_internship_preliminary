package com.example.demo;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 商品接口：发布商品、查询商品列表、商品详情、商品上下架。
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private static final String INSERT_SQL = """
            INSERT INTO products
                (title, price, stock, category, description, status, seller_name, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, 'on', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """;

    private static final String SELECT_SQL = """
            SELECT id, title, price, stock, category, description, status, seller_name
            FROM products
            """;

    private static final String UPDATE_STATUS_SQL =
            "UPDATE products SET status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

    private static final RowMapper<ProductItem> PRODUCT_ROW_MAPPER = (rs, rowNum) -> new ProductItem(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getInt("price"),
            rs.getInt("stock"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("seller_name")
    );

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    /** 商品不存在的业务码。 */
    private static final int PRODUCT_NOT_FOUND_CODE = 40401;

    private final JdbcTemplate jdbcTemplate;

    public ProductController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductCreated>> createProduct(
            @RequestBody CreateProductRequest request) {

        if (request == null) {
            throw BizException.badRequest("请求体不能为空");
        }

        String title = requireTitle(request.title());
        int price = requireNonNegativeInt(request.price(), "price");
        int stock = requireNonNegativeInt(request.stock(), "stock");
        String category = optionalText(request.category(), "category");
        String description = optionalText(request.description(), "description");
        String sellerName = optionalText(request.sellerName(), "sellerName");

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    INSERT_SQL, new String[]{"id"});
            ps.setString(1, title);
            ps.setInt(2, price);
            ps.setInt(3, stock);
            ps.setString(4, category);
            ps.setString(5, description);
            ps.setString(6, sellerName);
            return ps;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("商品插入成功，但未获取到自增主键");
        }
        long generatedId = generatedKey.longValue();

        ProductCreated data = new ProductCreated(
                generatedId,
                title,
                price,
                stock,
                category,
                description,
                "on",
                sellerName
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    @GetMapping
    public ApiResponse<ProductPage> listProducts(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "category", required = false) String category,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "pageSize", required = false) String pageSize) {

        int pageNo = parsePage(page);
        int size = parsePageSize(pageSize);
        String keywordFilter = trimToNull(keyword);
        String statusFilter = parseStatus(status);
        String categoryFilter = trimToNull(category);

        // 只拼接固定的 SQL 片段，用户输入一律走 ? 占位符，值放进 args
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (keywordFilter != null) {
            where.append(" AND title LIKE ? ESCAPE '\\'");
            args.add("%" + escapeLike(keywordFilter) + "%");
        }
        if (statusFilter != null) {
            where.append(" AND status = ?");
            args.add(statusFilter);
        }
        if (categoryFilter != null) {
            where.append(" AND category = ?");
            args.add(categoryFilter);
        }

        String whereClause = where.toString();

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products" + whereClause, Long.class, args.toArray());
        long totalCount = total == null ? 0L : total;

        long offset = (long) (pageNo - 1) * size;
        List<ProductItem> items;
        if (offset >= totalCount) {
            items = List.of();
        } else {
            List<Object> pageArgs = new ArrayList<>(args);
            pageArgs.add(size);
            pageArgs.add(offset);
            items = jdbcTemplate.query(
                    SELECT_SQL + whereClause + " ORDER BY id DESC LIMIT ? OFFSET ?",
                    PRODUCT_ROW_MAPPER,
                    pageArgs.toArray());
        }

        return ApiResponse.success(new ProductPage(totalCount, pageNo, size, items));
    }

    /**
     * 商品详情：id 不存在时返回 40401 / HTTP 404。
     */
    @GetMapping("/{id}")
    public ApiResponse<ProductItem> getProduct(@PathVariable("id") String id) {
        long productId = parseId(id);
        List<ProductItem> items = jdbcTemplate.query(
                SELECT_SQL + " WHERE id = ?", PRODUCT_ROW_MAPPER, productId);
        if (items.isEmpty()) {
            throw productNotFound();
        }
        return ApiResponse.success(items.get(0));
    }

    /**
     * 商品上下架：只接受 on / off，未更新到任何行说明商品不存在。
     */
    @PatchMapping("/{id}/status")
    public ApiResponse<Map<String, Object>> updateProductStatus(
            @PathVariable("id") String id,
            @RequestBody UpdateStatusRequest request) {

        long productId = parseId(id);
        if (request == null) {
            throw BizException.badRequest("请求体不能为空");
        }
        String status = requireStatus(request.status());

        int updated = jdbcTemplate.update(UPDATE_STATUS_SQL, status, productId);
        if (updated == 0) {
            throw productNotFound();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", productId);
        data.put("status", status);
        return ApiResponse.success(data);
    }

    private static BizException productNotFound() {
        return new BizException(PRODUCT_NOT_FOUND_CODE, "商品不存在", HttpStatus.NOT_FOUND);
    }

    private static long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.badRequest("id 必须是正整数");
        }
        long productId;
        try {
            productId = Long.parseLong(raw.strip());
        } catch (NumberFormatException ex) {
            throw BizException.badRequest("id 必须是正整数");
        }
        if (productId < 1) {
            throw BizException.badRequest("id 必须是正整数");
        }
        return productId;
    }

    /**
     * 上下架入参：必填，且只允许 on / off。
     */
    private static String requireStatus(Object value) {
        if (!(value instanceof String rawStatus)) {
            throw BizException.badRequest("status 只能是 on 或 off");
        }
        String status = rawStatus.strip().toLowerCase(Locale.ROOT);
        if (!"on".equals(status) && !"off".equals(status)) {
            throw BizException.badRequest("status 只能是 on 或 off");
        }
        return status;
    }

    private static int parsePage(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PAGE;
        }
        int pageNo = parseInteger(raw, "page");
        if (pageNo < 1) {
            throw BizException.badRequest("page 必须大于等于 1");
        }
        return pageNo;
    }

    private static int parsePageSize(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_PAGE_SIZE;
        }
        int size = parseInteger(raw, "pageSize");
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw BizException.badRequest("pageSize 必须在 1~50 之间");
        }
        return size;
    }

    private static int parseInteger(String raw, String fieldName) {
        try {
            return Integer.parseInt(raw.strip());
        } catch (NumberFormatException ex) {
            throw BizException.badRequest(fieldName + " 必须是整数");
        }
    }

    /**
     * 不传或传空串表示不筛选；其余值只允许 on / off。
     */
    private static String parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String status = raw.strip().toLowerCase(Locale.ROOT);
        if (!"on".equals(status) && !"off".equals(status)) {
            throw BizException.badRequest("status 只能是 on 或 off");
        }
        return status;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * 转义 LIKE 通配符，避免用户输入的 % 和 _ 被当作模糊匹配语法。
     */
    private static String escapeLike(String keyword) {
        StringBuilder escaped = new StringBuilder(keyword.length() + 8);
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            if (c == '\\' || c == '%' || c == '_') {
                escaped.append('\\');
            }
            escaped.append(c);
        }
        return escaped.toString();
    }

    private static String requireTitle(Object value) {
        if (!(value instanceof String rawTitle)) {
            throw BizException.badRequest("title 不能为空");
        }
        String title = rawTitle.strip();
        if (title.isEmpty()) {
            throw BizException.badRequest("title 不能为空");
        }
        if (title.codePointCount(0, title.length()) > 100) {
            throw BizException.badRequest("title 不能超过 100 字");
        }
        return title;
    }

    /**
     * 只接受 JSON 整数类型，字符串（如 "12"）、小数、布尔值、负数均拒绝。
     */
    private static int requireNonNegativeInt(Object value, String fieldName) {
        if (value instanceof Integer number) {
            if (number >= 0) {
                return number;
            }
        } else if (value instanceof Long number
                && number >= 0 && number <= Integer.MAX_VALUE) {
            return number.intValue();
        } else if (value instanceof BigInteger number
                && number.signum() >= 0 && number.bitLength() <= 31) {
            return number.intValue();
        }
        throw BizException.badRequest(fieldName + " 必须是非负整数");
    }

    private static String optionalText(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof String text)) {
            throw BizException.badRequest(fieldName + " 必须是字符串");
        }
        String stripped = text.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    /**
     * 使用 Object 接收字段，保留 JSON 原始类型，便于严格校验 number/string。
     */
    public record CreateProductRequest(
            Object title,
            Object price,
            Object stock,
            Object category,
            Object description,
            Object sellerName) {
    }

    /**
     * 上下架入参：{ "status": "on" | "off" }
     */
    public record UpdateStatusRequest(Object status) {
    }

    public record ProductCreated(
            long id,
            String title,
            int price,
            int stock,
            String category,
            String description,
            String status,
            String sellerName) {
    }

    /**
     * 列表分页结果：{ total, page, pageSize, items }
     */
    public record ProductPage(
            long total,
            int page,
            int pageSize,
            List<ProductItem> items) {
    }

    public record ProductItem(
            long id,
            String title,
            int price,
            int stock,
            String category,
            String description,
            String status,
            String sellerName) {
    }
}