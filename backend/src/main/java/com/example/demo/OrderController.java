package com.example.demo;

import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 订单接口：下单、订单详情、订单列表。
 * 扣减库存与插入订单在同一事务内，任一步失败整体回滚。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final String SELECT_PRODUCT_SQL = """
            SELECT id, title, price, stock, status
            FROM products
            WHERE id = ?
            FOR UPDATE
            """;

    private static final String DEDUCT_STOCK_SQL =
            "UPDATE products SET stock = stock - ? WHERE id = ?";

    private static final String INSERT_ORDER_SQL = """
            INSERT INTO orders
                (order_no, product_id, product_title, unit_price, quantity, total_amount,
                 buyer_name, buyer_phone, address, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'created')
            """;

    private static final String MAX_ORDER_NO_SQL =
            "SELECT MAX(order_no) FROM orders WHERE order_no LIKE ?";

    /** 订单表全字段查询，详情与列表复用。 */
    private static final String SELECT_ORDER_SQL = """
            SELECT id, order_no, product_id, product_title, unit_price, quantity,
                   total_amount, buyer_name, buyer_phone, address, status, created_at, paid_at
            FROM orders
            """;

    private static final RowMapper<Product> PRODUCT_ROW_MAPPER = (rs, rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getInt("price"),
            rs.getInt("stock"),
            rs.getString("status")
    );

    private static final RowMapper<OrderDetail> ORDER_DETAIL_ROW_MAPPER = (rs, rowNum) -> new OrderDetail(
            rs.getLong("id"),
            rs.getString("order_no"),
            rs.getLong("product_id"),
            rs.getString("product_title"),
            rs.getInt("unit_price"),
            rs.getInt("quantity"),
            rs.getInt("total_amount"),
            rs.getString("buyer_name"),
            rs.getString("buyer_phone"),
            rs.getString("address"),
            rs.getString("status"),
            toLocalDateTime(rs.getTimestamp("created_at")),
            toLocalDateTime(rs.getTimestamp("paid_at"))
    );

    private static final DateTimeFormatter ORDER_NO_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 商品不存在的业务码。 */
    private static final int PRODUCT_NOT_FOUND_CODE = 40401;
    /** 订单不存在的业务码。 */
    private static final int ORDER_NOT_FOUND_CODE = 40401;
    /** 库存不足的业务码。 */
    private static final int STOCK_NOT_ENOUGH_CODE = 40901;
    /** 商品已下架的业务码。 */
    private static final int PRODUCT_OFF_SHELF_CODE = 40902;

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 50;

    private final JdbcTemplate jdbcTemplate;

    public OrderController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 下单：校验参数 -> 查商品（加行锁防超卖） -> 校验状态与库存 -> 扣库存 -> 生成订单号 -> 插入订单。
     * 全程在一个事务里，插入订单失败时已扣减的库存会自动回滚。
     */
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<OrderCreated>> createOrder(
            @RequestBody CreateOrderRequest request) {

        if (request == null) {
            throw BizException.badRequest("请求体不能为空");
        }

        long productId = requirePositiveLong(request.productId(), "productId");
        int quantity = requirePositiveInt(request.quantity(), "quantity");
        String buyerName = requireText(request.buyerName(), "buyerName", 100);
        String buyerPhone = requireText(request.buyerPhone(), "buyerPhone", 20);
        String address = requireText(request.address(), "address", 500);

        // 查商品并加行锁，防止并发下单超卖
        List<Product> products = jdbcTemplate.query(SELECT_PRODUCT_SQL, PRODUCT_ROW_MAPPER, productId);
        if (products.isEmpty()) {
            throw productNotFound();
        }
        Product product = products.get(0);

        if ("off".equals(product.status())) {
            throw new BizException(PRODUCT_OFF_SHELF_CODE, "商品已下架", HttpStatus.CONFLICT);
        }
        if (product.stock() < quantity) {
            throw new BizException(STOCK_NOT_ENOUGH_CODE, "库存不足", HttpStatus.CONFLICT);
        }

        // 扣减库存
        int updated = jdbcTemplate.update(DEDUCT_STOCK_SQL, quantity, productId);
        if (updated == 0) {
            throw new IllegalStateException("扣减库存失败：商品不存在或已被删除");
        }

        // 单价、总价全部以数据库查出的商品价格为准，绝不信任前端传价
        int unitPrice = product.price();
        long totalLong = (long) unitPrice * quantity;
        if (totalLong > Integer.MAX_VALUE) {
            throw new IllegalStateException("订单总金额超出范围");
        }
        int totalAmount = (int) totalLong;

        // 生成唯一订单号
        String orderNo = generateOrderNo();

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_ORDER_SQL, new String[]{"id"});
            ps.setString(1, orderNo);
            ps.setLong(2, productId);
            ps.setString(3, product.title());
            ps.setInt(4, unitPrice);
            ps.setInt(5, quantity);
            ps.setInt(6, totalAmount);
            ps.setString(7, buyerName);
            ps.setString(8, buyerPhone);
            ps.setString(9, address);
            return ps;
        }, keyHolder);

        Number generatedKey = keyHolder.getKey();
        if (generatedKey == null) {
            throw new IllegalStateException("订单插入成功，但未获取到自增主键");
        }
        long orderId = generatedKey.longValue();

        OrderCreated data = new OrderCreated(
                orderId,
                orderNo,
                productId,
                product.title(),
                unitPrice,
                quantity,
                totalAmount,
                buyerName,
                buyerPhone,
                address,
                "created"
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(data));
    }

    /**
     * 订单详情：根据订单号 orderNo 查询，不存在时返回 404 / 40401。
     */
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDetail> getOrder(@PathVariable("orderNo") String orderNo) {
        String orderNoValue = requireOrderNo(orderNo);
        List<OrderDetail> orders = jdbcTemplate.query(
                SELECT_ORDER_SQL + " WHERE order_no = ?", ORDER_DETAIL_ROW_MAPPER, orderNoValue);
        if (orders.isEmpty()) {
            throw orderNotFound();
        }
        return ApiResponse.success(orders.get(0));
    }

    /**
     * 订单列表：按买家手机号查询，按 created_at 倒序，支持分页。
     */
    @GetMapping
    public ApiResponse<OrderPage> listOrders(
            @RequestParam(name = "buyerPhone", required = false) String buyerPhone,
            @RequestParam(name = "page", required = false) String page,
            @RequestParam(name = "pageSize", required = false) String pageSize) {

        int pageNo = parsePage(page);
        int size = parsePageSize(pageSize);
        String phone = requireBuyerPhone(buyerPhone);

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM orders WHERE buyer_phone = ?", Long.class, phone);
        long totalCount = total == null ? 0L : total;

        long offset = (long) (pageNo - 1) * size;
        List<OrderDetail> items;
        if (offset >= totalCount) {
            items = List.of();
        } else {
            items = jdbcTemplate.query(
                    SELECT_ORDER_SQL + " WHERE buyer_phone = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                    ORDER_DETAIL_ROW_MAPPER,
                    phone, size, offset);
        }

        return ApiResponse.success(new OrderPage(totalCount, pageNo, size, items));
    }

    /**
     * 生成订单号：SO + yyyyMMdd + 4 位序列号，例如 SO202611250001。
     * 序列号取当天已有最大订单号 +1；order_no 上的唯一约束兜底保证唯一。
     */
    private String generateOrderNo() {
        String prefix = "SO" + LocalDate.now().format(ORDER_NO_DATE_FORMAT);
        String maxOrderNo = jdbcTemplate.queryForObject(MAX_ORDER_NO_SQL, String.class, prefix + "%");
        int seq = 0;
        if (maxOrderNo != null && maxOrderNo.startsWith(prefix)) {
            String suffix = maxOrderNo.substring(prefix.length());
            try {
                seq = Integer.parseInt(suffix);
            } catch (NumberFormatException ex) {
                seq = 0;
            }
        }
        return prefix + String.format(Locale.ROOT, "%04d", seq + 1);
    }

    private static BizException productNotFound() {
        return new BizException(PRODUCT_NOT_FOUND_CODE, "商品不存在", HttpStatus.NOT_FOUND);
    }

    private static BizException orderNotFound() {
        return new BizException(ORDER_NOT_FOUND_CODE, "订单不存在", HttpStatus.NOT_FOUND);
    }

    private static String requireOrderNo(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.badRequest("orderNo 不能为空");
        }
        return raw.strip();
    }

    /**
     * buyerPhone 必填：为空或空白抛参数错误。
     */
    private static String requireBuyerPhone(String raw) {
        if (raw == null || raw.isBlank()) {
            throw BizException.badRequest("buyerPhone 不能为空");
        }
        String phone = raw.strip();
        if (phone.codePointCount(0, phone.length()) > 20) {
            throw BizException.badRequest("buyerPhone 不能超过 20 字");
        }
        return phone;
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
     * 只接受 JSON 整数类型且 >= 1；字符串（如 "2"）、小数、布尔值、0、负数均拒绝。
     */
    private static int requirePositiveInt(Object value, String fieldName) {
        if (value instanceof Integer number && number >= 1) {
            return number;
        }
        if (value instanceof Long number && number >= 1 && number <= Integer.MAX_VALUE) {
            return number.intValue();
        }
        if (value instanceof BigInteger number && number.signum() > 0 && number.bitLength() <= 31) {
            return number.intValue();
        }
        throw BizException.badRequest(fieldName + " 必须是大于等于 1 的整数");
    }

    /**
     * 只接受 JSON 整数类型且 >= 1。
     */
    private static long requirePositiveLong(Object value, String fieldName) {
        if (value instanceof Integer number && number >= 1) {
            return number.longValue();
        }
        if (value instanceof Long number && number >= 1) {
            return number;
        }
        if (value instanceof BigInteger number && number.signum() > 0 && number.bitLength() <= 63) {
            return number.longValue();
        }
        throw BizException.badRequest(fieldName + " 必须是正整数");
    }

    /**
     * 必填文本字段：必须是字符串、非空白，且不超过 maxLength 字。
     */
    private static String requireText(Object value, String fieldName, int maxLength) {
        if (!(value instanceof String text)) {
            throw BizException.badRequest(fieldName + " 不能为空");
        }
        String stripped = text.strip();
        if (stripped.isEmpty()) {
            throw BizException.badRequest(fieldName + " 不能为空");
        }
        if (stripped.codePointCount(0, stripped.length()) > maxLength) {
            throw BizException.badRequest(fieldName + " 不能超过 " + maxLength + " 字");
        }
        return stripped;
    }

    private static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts == null ? null : ts.toLocalDateTime();
    }

    /**
     * 下单入参：{ "productId": 1, "quantity": 2, "buyerName": "...", ... }
     * 字段使用 Object 保留 JSON 原始类型，便于严格校验。
     */
    public record CreateOrderRequest(
            Object productId,
            Object quantity,
            Object buyerName,
            Object buyerPhone,
            Object address) {
    }

    /**
     * 下单所需的商品快照（内部行映射）。
     */
    public record Product(
            long id,
            String title,
            int price,
            int stock,
            String status) {
    }

    /**
     * 下单成功返回的订单详情。
     */
    public record OrderCreated(
            long id,
            String orderNo,
            long productId,
            String productTitle,
            int unitPrice,
            int quantity,
            int totalAmount,
            String buyerName,
            String buyerPhone,
            String address,
            String status) {
    }

    /**
     * 订单全字段详情（查询返回），包含 created_at、paid_at。
     */
    public record OrderDetail(
            long id,
            String orderNo,
            long productId,
            String productTitle,
            int unitPrice,
            int quantity,
            int totalAmount,
            String buyerName,
            String buyerPhone,
            String address,
            String status,
            LocalDateTime createdAt,
            LocalDateTime paidAt) {
    }

    /**
     * 订单列表分页结果：{ total, page, pageSize, items }
     */
    public record OrderPage(
            long total,
            int page,
            int pageSize,
            List<OrderDetail> items) {
    }
}