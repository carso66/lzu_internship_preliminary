package com.lzu.marketplace.service;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.mapper.OrderMapper;
import com.lzu.marketplace.mapper.ProductMapper;
import com.lzu.marketplace.pojo.Order;
import com.lzu.marketplace.pojo.Product;
import com.lzu.marketplace.pojo.dto.OrderCreateRequest;
import com.lzu.marketplace.pojo.dto.OrderListResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 订单业务：下单（扣库存 + 写订单，同一事务）、按订单号查详情、按手机号查列表。
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final int PAGE_SIZE_MAX = 50;
    private static final int BUYER_NAME_MAX = 50;
    private static final int BUYER_PHONE_MAX = 20;
    private static final int ADDRESS_MAX = 200;

    private static final String ORDER_NO_PREFIX = "SO";
    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int ORDER_NO_MAX_ATTEMPTS = 5;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private ProductMapper productMapper;

    /**
     * 下单：校验 -> 查商品（不存在 404、已下架 40902）-> 校验库存（不足 40901）
     * -> 条件扣库存 -> 写订单（单价/总价取自数据库价格，订单号服务端生成）。
     * 扣库存与写订单在同一个事务里：写订单失败会连带回滚库存。
     */
    @Transactional
    public Order create(OrderCreateRequest request) {
        if (request == null) {
            throw badRequest("请求体不能为空");
        }

        Long productId = request.getProductId();
        if (productId == null) {
            throw badRequest("productId 不能为空");
        }

        Integer quantity = request.getQuantity();
        if (quantity == null) {
            throw badRequest("quantity 不能为空");
        }
        if (quantity < 1) {
            throw badRequest("quantity 必须大于等于 1");
        }

        String buyerName = trimToNull(request.getBuyerName());
        if (buyerName == null) {
            throw badRequest("buyerName 不能为空");
        }
        if (buyerName.length() > BUYER_NAME_MAX) {
            throw badRequest("buyerName 长度不能超过 " + BUYER_NAME_MAX);
        }
        String buyerPhone = trimToNull(request.getBuyerPhone());
        if (buyerPhone == null) {
            throw badRequest("buyerPhone 不能为空");
        }
        if (buyerPhone.length() > BUYER_PHONE_MAX) {
            throw badRequest("buyerPhone 长度不能超过 " + BUYER_PHONE_MAX);
        }
        if (!buyerPhone.matches("\\d{5,20}")) {
            throw badRequest("buyerPhone 格式不正确");
        }
        String address = trimToNull(request.getAddress());
        if (address == null) {
            throw badRequest("address 不能为空");
        }
        if (address.length() > ADDRESS_MAX) {
            throw badRequest("address 长度不能超过 " + ADDRESS_MAX);
        }

        Product product = productMapper.findById(productId);
        if (product == null) {
            throw new BusinessException(40401, 404, "商品不存在");
        }
        if (!"on".equals(product.getStatus())) {
            throw new BusinessException(40902, 409, "商品已下架");
        }
        if (product.getStock() == null || product.getStock() < quantity) {
            throw new BusinessException(40901, 409, "库存不足");
        }

        long totalAmount = (long) product.getPrice() * quantity;
        if (totalAmount > Integer.MAX_VALUE) {
            throw badRequest("订单总金额超出范围");
        }

        // 带条件的扣减：并发下只有真正扣到库存的请求才继续，避免超卖。
        // 条件里同时要求 status = 'on'，防止「查完商品后被别人下架」的中间态漏进来。
        int deducted = productMapper.deductStock(productId, quantity);
        if (deducted == 0) {
            // 没扣到：可能库存被别人抢走了，也可能刚好被下架，重新读一次给出准确原因
            Product latest = productMapper.findById(productId);
            if (latest == null) {
                throw new BusinessException(40401, 404, "商品不存在");
            }
            if (!"on".equals(latest.getStatus())) {
                throw new BusinessException(40902, 409, "商品已下架");
            }
            throw new BusinessException(40901, 409, "库存不足");
        }

        Order order = new Order();
        order.setProductId(productId);
        order.setProductTitle(product.getTitle());
        order.setQuantity(quantity);
        order.setUnitPrice(product.getPrice());
        order.setTotalAmount((int) totalAmount);
        order.setBuyerName(buyerName);
        order.setBuyerPhone(buyerPhone);
        order.setAddress(address);
        order.setStatus("created");
        insertWithUniqueOrderNo(order);

        return orderMapper.findById(order.getId());
    }

    /** 订单详情：按订单号查，不存在 404 + 40401。 */
    public Order findByOrderNo(String orderNo) {
        String value = trimToNull(orderNo);
        Order order = (value == null) ? null : orderMapper.findByOrderNo(value);
        if (order == null) {
            throw new BusinessException(40401, 404, "订单不存在");
        }
        return order;
    }

    /** 订单列表：按手机号筛选（不传则返回全部），创建时间倒序。 */
    public OrderListResponse page(String buyerPhone, int page, int pageSize) {
        if (page < 1) {
            throw badRequest("page 必须大于等于 1");
        }
        if (pageSize < 1 || pageSize > PAGE_SIZE_MAX) {
            throw badRequest("pageSize 必须在 1~" + PAGE_SIZE_MAX + " 之间");
        }
        String phoneFilter = trimToNull(buyerPhone);
        long offset = (long) (page - 1) * pageSize;
        long total = orderMapper.count(phoneFilter);
        List<Order> items = orderMapper.page(phoneFilter, offset, pageSize);
        return new OrderListResponse(total, page, pageSize, items);
    }

    /**
     * 生成 order_no（SO + yyyyMMdd + 4 位当日序号）并插入。
     * 并发下同一天可能撞号，这里退避重试几次。
     */
    private void insertWithUniqueOrderNo(Order order) {
        String dayPrefix = ORDER_NO_PREFIX + LocalDate.now().format(DAY_FORMAT);
        for (int attempt = 0; attempt < ORDER_NO_MAX_ATTEMPTS; attempt++) {
            order.setOrderNo(dayPrefix + String.format("%04d", nextSequence(dayPrefix) + attempt));
            try {
                orderMapper.insert(order);
                return;
            } catch (DataIntegrityViolationException e) {
                log.warn("订单号冲突，重试第 {} 次: {}", attempt + 1, order.getOrderNo());
            }
        }
        throw new BusinessException(50000, 500, "生成订单号失败，请重试");
    }

    private int nextSequence(String dayPrefix) {
        String max = orderMapper.maxOrderNo(dayPrefix + "%");
        if (max == null || max.length() <= dayPrefix.length()) {
            return 1;
        }
        try {
            return Integer.parseInt(max.substring(dayPrefix.length())) + 1;
        } catch (NumberFormatException e) {
            return 1;
        }
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
