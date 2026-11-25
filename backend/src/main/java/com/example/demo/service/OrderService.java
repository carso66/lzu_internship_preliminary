package com.example.demo.service;

import com.example.demo.common.BizException;
import com.example.demo.common.ErrorCode;
import com.example.demo.common.PageResult;
import com.example.demo.dto.CreateOrderRequest;
import com.example.demo.model.Order;
import com.example.demo.model.Product;
import com.example.demo.repository.OrderRepository;
import com.example.demo.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

@Service
public class OrderService {

    private static final DateTimeFormatter ORDER_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMdd");

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    public OrderService(
            OrderRepository orderRepository,
            ProductRepository productRepository
    ) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
    }

    /**
     * 创建订单。
     *
     * 事务范围：
     * 1. 锁定商品行
     * 2. 校验商品状态
     * 3. 校验库存并计算金额
     * 4. 扣减库存
     * 5. 插入订单
     *
     * 扣库存和插入订单处于同一个事务中。
     */
    @Transactional
    public Order create(CreateOrderRequest request) {
        CreateOrderRequest normalizedRequest =
                normalizeAndValidateCreateRequest(request);

        long productId = normalizedRequest.productId();
        int quantity = normalizedRequest.quantity();

        /*
         * 必须使用行锁查询。
         * findByIdForUpdate() 在当前事务中执行，商品行锁会一直持有到
         * 下单事务提交或回滚。
         */
        Product product = productRepository
                .findByIdForUpdate(productId)
                .orElseThrow(() -> new BizException(
                        ErrorCode.NOT_FOUND,
                        "商品不存在"
                ));

        if (!"on".equals(product.status())) {
            throw new BizException(
                    ErrorCode.PRODUCT_OFF,
                    "商品已下架"
            );
        }

        /*
         * 当前商品行已被锁定，因此此处读取到的库存不会在本次事务内
         * 被其他下单事务修改。
         */
        if (product.stock() < quantity) {
            throw new BizException(
                    ErrorCode.STOCK_NOT_ENOUGH,
                    "库存不足"
            );
        }

        long unitPrice = product.price();

        /*
         * 在扣库存前计算金额。
         * 如果乘法溢出，抛出参数错误，库存尚未发生变化。
         */
        long totalAmount = calculateTotalAmount(
                unitPrice,
                quantity
        );

        String orderNo = generateOrderNo();

        /*
         * 保留条件扣库存 SQL，作为库存扣减的最终保护。
         */
        int affectedRows = productRepository.deductStock(
                productId,
                quantity
        );

        if (affectedRows == 0) {
            throw new BizException(
                    ErrorCode.STOCK_NOT_ENOUGH,
                    "库存不足"
            );
        }

        /*
         * 保存商品和价格快照。
         * unitPrice 和 totalAmount 均由服务端计算，不接受客户端传值。
         */
        Order order = new Order(
                0L,
                orderNo,
                productId,
                product.title(),
                quantity,
                unitPrice,
                totalAmount,
                normalizedRequest.buyerName(),
                normalizedRequest.buyerPhone(),
                normalizedRequest.address(),
                normalizedRequest.remark(),
                "created",
                null,
                null,
                null
        );

        long orderId = orderRepository.insert(order);

        return orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalStateException(
                        "新增订单后查询失败"
                ));
    }

    /**
     * 按订单号查询订单详情。
     */
    public Order getByOrderNo(String orderNo) {
        String normalizedOrderNo =
                normalizeRequiredText(orderNo, "orderNo", 64);

        return orderRepository.findByOrderNo(normalizedOrderNo)
                .orElseThrow(() -> new BizException(
                        ErrorCode.NOT_FOUND,
                        "订单不存在"
                ));
    }

    /**
     * 按买家手机号分页查询订单。
     */
    public PageResult<Order> listByBuyerPhone(
            String buyerPhone,
            int page,
            int pageSize
    ) {
        if (page < 1) {
            throw invalid("page 必须大于等于 1");
        }

        if (pageSize < 1 || pageSize > 50) {
            throw invalid("pageSize 必须在 1 到 50 之间");
        }

        String normalizedPhone = normalizeBuyerPhone(buyerPhone);

        return orderRepository.findPageByBuyerPhone(
                normalizedPhone,
                page,
                pageSize
        );
    }

    /**
     * 校验并规范化下单请求。
     */
    private CreateOrderRequest normalizeAndValidateCreateRequest(
            CreateOrderRequest request
    ) {
        if (request == null) {
            throw invalid("请求体不能为空");
        }

        Long productId = request.productId();

        if (productId == null || productId <= 0) {
            throw invalid("productId 必须是正整数");
        }

        Integer quantity = request.quantity();

        if (quantity == null || quantity < 1) {
            throw invalid(
                    "quantity 必须是大于等于 1 的整数"
            );
        }

        String buyerName = normalizeRequiredText(
                request.buyerName(),
                "buyerName",
                50
        );

        String buyerPhone = normalizeBuyerPhone(
                request.buyerPhone()
        );

        String address = normalizeRequiredText(
                request.address(),
                "address",
                500
        );

        String remark = normalizeOptionalText(
                request.remark(),
                "remark",
                500
        );

        return new CreateOrderRequest(
                productId,
                quantity,
                buyerName,
                buyerPhone,
                address,
                remark
        );
    }

    /**
     * 必填文本：校验非空、去除首尾空白、校验长度。
     */
    private String normalizeRequiredText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null || value.isBlank()) {
            throw invalid(fieldName + " 不能为空");
        }

        String normalized = value.strip();

        if (normalized.length() > maxLength) {
            throw invalid(
                    fieldName + " 长度不能超过 " + maxLength
            );
        }

        return normalized;
    }

    /**
     * 可选文本：未传时使用空字符串。
     */
    private String normalizeOptionalText(
            String value,
            String fieldName,
            int maxLength
    ) {
        if (value == null) {
            return "";
        }

        String normalized = value.strip();

        if (normalized.length() > maxLength) {
            throw invalid(
                    fieldName + " 长度不能超过 " + maxLength
            );
        }

        return normalized;
    }

    /**
     * 手机号保存和查询使用同一套规范化规则。
     */
    private String normalizeBuyerPhone(String buyerPhone) {
        if (buyerPhone == null || buyerPhone.isBlank()) {
            throw invalid("buyerPhone 不能为空");
        }

        String normalized = buyerPhone.strip();

        if (normalized.length() > 20) {
            throw invalid("buyerPhone 长度不能超过 20");
        }

        return normalized;
    }

    /**
     * 使用精确整数运算计算总金额，避免 long 溢出。
     */
    private long calculateTotalAmount(
            long unitPrice,
            int quantity
    ) {
        try {
            return Math.multiplyExact(
                    unitPrice,
                    (long) quantity
            );
        } catch (ArithmeticException e) {
            throw invalid("订单金额超出允许范围");
        }
    }

    /**
     * 订单号格式：SO + yyyyMMdd + 完整 UUID。
     *
     * 总长度为 42，不超过 order_no VARCHAR(64) 的限制。
     * 数据库唯一约束 uk_orders_order_no 继续作为最终保障。
     */
    private String generateOrderNo() {
        String date = LocalDate.now().format(
                ORDER_DATE_FORMATTER
        );

        String randomPart = UUID.randomUUID()
                .toString()
                .replace("-", "")
                .toUpperCase(Locale.ROOT);

        return "SO" + date + randomPart;
    }

    private BizException invalid(String message) {
        return new BizException(
                ErrorCode.PARAM_INVALID,
                message
        );
    }
}
