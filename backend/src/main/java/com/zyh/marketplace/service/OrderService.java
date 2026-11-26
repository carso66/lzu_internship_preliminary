package com.zyh.marketplace.service;

import com.zyh.marketplace.common.ErrorCode;
import com.zyh.marketplace.common.PageResult;
import com.zyh.marketplace.common.ProductStatus;
import com.zyh.marketplace.dto.CreateOrderRequest;
import com.zyh.marketplace.dto.OrderQuery;
import com.zyh.marketplace.dto.OrderResponse;
import com.zyh.marketplace.entity.Order;
import com.zyh.marketplace.entity.Product;
import com.zyh.marketplace.exception.BusinessException;
import com.zyh.marketplace.mapper.OrderMapper;
import com.zyh.marketplace.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * 订单业务（L1-6 ~ L1-8）：参数校验 + 服务端算价 + 事务边界。
 *
 * <p><b>事务与并发方案</b>：下单在同一个 @Transactional 方法内完成
 * 「原子扣库存 → 算价 → 插订单」；扣库存用一条带条件的原子 UPDATE
 * （WHERE stock >= ? AND status = 'on'），利用数据库行锁天然串行化并发，
 * 不依赖 JVM 锁 / Redis / 分布式锁；插订单失败时整体回滚，库存自动恢复。
 * 该方案与 schema.sql 注释、DESIGN.md 2.2 节的既定约定一致，L2-10 直接沿用。
 *
 * <p><b>回查区分 40901 / 40902</b>：受影响行数为 0 时不猜测原因，回查一次商品行——
 * off → 40902，其余（库存不足）→ 40901。
 */
@Service
public class OrderService {

    /** 与 orders.buyer_name VARCHAR(64) 对应 */
    private static final int BUYER_NAME_MAX = 64;
    /** 与 orders.buyer_phone VARCHAR(20) 对应 */
    private static final int BUYER_PHONE_MAX = 20;
    /** 与 orders.address VARCHAR(200) 对应 */
    private static final int ADDRESS_MAX = 200;
    /** 新订单状态，与 schema.sql CHECK 约束一致 */
    private static final String STATUS_CREATED = "created";

    /** 与 orders.order_no VARCHAR(32) 对应：SO(2) + 时间戳(14) + 序号(≥6) ≤ 32 */
    private static final String ORDER_NO_PREFIX = "SO";
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    /** 订单号最大长度（与 orders.order_no VARCHAR(32) 一致），生成后强制断言 */
    private static final int ORDER_NO_MAX = 32;

    private static final int PAGE_SIZE_MAX = 50;
    /** 页码上限：与 offset 的 int 范围配合，防止分页偏移量溢出（与商品列表一致） */
    private static final long PAGE_MAX = 10_000_000L;

    /** 纯整数文本：可带正负号，不允许小数点 / 科学计数 */
    private static final Pattern INTEGER_TEXT = Pattern.compile("-?\\d{1,19}");
    /** 手机号：纯数字（收货联系电话，最多 20 位，兼容固话带区号） */
    private static final Pattern PHONE_TEXT = Pattern.compile("\\d{1,20}");

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 进程内单调序号，用于订单号后 6 位；种子取进程启动时的毫秒数（模百万），
     * 降低重启后同一秒内序号回退的概率。唯一性的最终兜底是 orders.order_no 的
     * UNIQUE 约束——极端情况下（时钟回拨 + 同秒重启）插入会失败回滚并返回 50000，
     * 绝不可能产生重复订单。
     */
    private static final AtomicLong ORDER_SEQ = new AtomicLong(System.currentTimeMillis() % 1_000_000L);

    private final OrderMapper orderMapper;
    private final ProductMapper productMapper;

    public OrderService(OrderMapper orderMapper, ProductMapper productMapper) {
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
    }

    // ------------------------------------------------------------------ L1-6 下单

    /**
     * 下单：扣库存与插订单在同一事务。价格一律取数据库快照，不接受客户端任何价格字段。
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request) {
        rejectUnknownField(request.getUnknownField(), "productId, quantity, buyerName, buyerPhone, address");

        long productId = requiredLong("productId", request.getProductId(), 1L, Long.MAX_VALUE);
        long quantityValue = requiredLong("quantity", request.getQuantity(), 1L, Integer.MAX_VALUE);
        int quantity = (int) quantityValue;
        String buyerName = requiredText("buyerName", request.getBuyerName(), BUYER_NAME_MAX);
        String buyerPhone = requiredPhone("buyerPhone", request.getBuyerPhone());
        String address = requiredText("address", request.getAddress(), ADDRESS_MAX);

        // 1. 查商品：不存在 → 404；先做一次下架预检，让 40902 的报错更直接（原子 UPDATE 内还会再挡一次）
        Product product = productMapper.findById(productId);
        if (product == null) {
            throw BusinessException.notFound("商品不存在：id=" + productId);
        }
        if (ProductStatus.OFF.equals(product.getStatus())) {
            throw BusinessException.productOff("商品已下架：" + product.getTitle());
        }

        // 2. 原子扣库存：条件并进 UPDATE；受影响行数为 0 说明「不存在 / 已下架 / 库存不足」之一
        int affected = productMapper.deductStockIfAvailable(productId, quantity);
        if (affected == 0) {
            // 回查一次区分原因；此时未对数据做任何修改，直接抛错即回滚
            Product fresh = productMapper.findById(productId);
            if (fresh == null) {
                throw BusinessException.notFound("商品不存在：id=" + productId);
            }
            if (ProductStatus.OFF.equals(fresh.getStatus())) {
                throw BusinessException.productOff("商品已下架：" + fresh.getTitle());
            }
            throw BusinessException.insufficientStock("库存不足：当前库存 " + fresh.getStock() + "，需要 " + quantity);
        }

        // 3. 服务端算价（以扣减时读到的数据库单价为准），Math.multiplyExact 防溢出
        long unitPrice = product.getPrice();
        long totalAmount;
        try {
            totalAmount = Math.multiplyExact(unitPrice, quantity);
        } catch (ArithmeticException overflow) {
            // 在 price ≤ 999999999 分、quantity ≤ Integer.MAX_VALUE 的校验上限下实际不可达；
            // 命中说明币数量出现脏数据：按服务错误处理并触发回滚
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "订单金额超出可表示范围");
        }

        // 4. 插订单（含商品标题 / 单价快照），UNIQUE(order_no) 是唯一性的最后兜底
        Order order = new Order();
        order.setOrderNo(nextOrderNo());
        order.setProductId(productId);
        order.setProductTitle(product.getTitle());
        order.setQuantity(quantity);
        order.setUnitPrice(unitPrice);
        order.setTotalAmount(totalAmount);
        order.setBuyerName(buyerName);
        order.setBuyerPhone(buyerPhone);
        order.setAddress(address);
        order.setStatus(STATUS_CREATED);
        orderMapper.insert(order);

        // 5. 回读一次，createdAt 以数据库默认值 CURRENT_TIMESTAMP 为准
        Order saved = orderMapper.findById(order.getId());
        if (saved == null) {
            throw new IllegalStateException("订单写入后无法读回：orderNo=" + order.getOrderNo());
        }
        return toResponse(saved);
    }

    // ------------------------------------------------------------------ L1-7 订单详情

    /**
     * 按订单号查详情。orderNo 拼写或长度（>32）不合法时必然查不到，按不存在处理（40401）。
     */
    @Transactional(readOnly = true)
    public OrderResponse detail(String orderNo) {
        Order order = orderMapper.findByOrderNo(orderNo);
        if (order == null) {
            throw BusinessException.notFound("订单不存在：orderNo=" + orderNo);
        }
        return toResponse(order);
    }

    // ------------------------------------------------------------------ L1-8 订单列表

    /**
     * 按手机号精确查询，创建时间倒序（id 唯一序保证稳定），数据库分页。
     * 分页参数校验与商品列表同一套约定（page 默认 1、pageSize 默认 10、范围 1~50）。
     */
    @Transactional(readOnly = true)
    public PageResult<OrderResponse> list(OrderQuery query) {
        String buyerPhone = requiredPhone("buyerPhone", query.buyerPhone());
        int page = positiveInt("page", query.page(), 1L, PAGE_MAX);
        int pageSize = positiveInt("pageSize", query.pageSize(), 1L, PAGE_SIZE_MAX);

        // offset 先用 long 计算再判断是否超出 int 范围，避免 int 乘法溢出成负数
        long offset = (long) (page - 1) * pageSize;
        if (offset > Integer.MAX_VALUE) {
            throw BusinessException.badRequest("字段 page 过大：page * pageSize 超出的分页偏移量上限");
        }

        long total = orderMapper.countByBuyerPhone(buyerPhone);
        List<Order> orders = orderMapper.findPageByBuyerPhone(buyerPhone, (int) offset, pageSize);
        List<OrderResponse> items = orders.stream().map(OrderService::toResponse).toList();
        return new PageResult<>(total, page, pageSize, items);
    }

    // ------------------------------------------------------------------ 校验与工具

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

    /** 手机号：必填、去空白、纯数字、1~20 位（与 orders.buyer_phone VARCHAR(20) 对齐）。
     *  下单与列表查询共用同一规则，避免「能下单却查不到」或「能查到却不是同一种值」。 */
    private String requiredPhone(String field, String raw) {
        if (raw == null) {
            throw BusinessException.badRequest("字段 " + field + " 不能为空");
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            throw BusinessException.badRequest("字段 " + field + " 不能为空");
        }
        if (!PHONE_TEXT.matcher(trimmed).matches()) {
            throw BusinessException.badRequest("字段 " + field + " 必须是 1~" + BUYER_PHONE_MAX + " 位纯数字");
        }
        return trimmed;
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

    /**
     * 订单号生成：SO + yyyyMMddHHmmss + 单调序号。
     * <p>序号用 %06d 格式化——注意 %06d 是**最小宽度**（不足补零、超出按原样），
     * 不是最大宽度：序号超过 6 位时会自然变长（如 1000000 → 1000000），
     * 订单号最长也只到 2+14+19=35 的理论极端，正常运行（序号 ≤ 7 位）为 22~23 字符，
     * 远小于 orders.order_no 的 VARCHAR(32)。
     * <p>这里额外做一次长度断言：一旦未来有人改动格式导致超长，会立刻以 50000 失败，
     * 而不是把超长字符串送进数据库。
     * <p>唯一性：同一进程内「时间戳 + 单调序号」保证不重复；跨进程由 UNIQUE(order_no) 兜底。
     */
    private static String nextOrderNo() {
        String orderNo = ORDER_NO_PREFIX + LocalDateTime.now().format(ORDER_NO_TIME)
                + String.format("%06d", ORDER_SEQ.incrementAndGet());
        if (orderNo.length() > ORDER_NO_MAX) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "订单号长度超过 " + ORDER_NO_MAX + " 个字符：" + orderNo.length());
        }
        return orderNo;
    }

    static OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getOrderNo(),
                order.getProductId(),
                order.getProductTitle(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getBuyerName(),
                order.getBuyerPhone(),
                order.getAddress(),
                order.getPaidAt() == null ? null : order.getPaidAt().format(TIME_FORMAT),
                order.getCreatedAt() == null ? null : order.getCreatedAt().format(TIME_FORMAT)
        );
    }
}

