package com.lzu.xy.service;

import com.lzu.xy.controller.model.dto.OrderCreateDTO;
import com.lzu.xy.controller.model.dto.OrderQueryDTO;
import com.lzu.xy.controller.model.vo.OrderVO;
import com.lzu.xy.controller.model.vo.PageVO;
import com.lzu.xy.domain.Order;
import com.lzu.xy.domain.Product;
import com.lzu.xy.enums.OrderStatusEnum;
import com.lzu.xy.enums.ProductStatusEnum;
import com.lzu.xy.enums.ResultCodeEnum;
import com.lzu.xy.exception.BusinessException;
import com.lzu.xy.repository.OrderRepository;
import com.lzu.xy.repository.ProductRepository;
import com.lzu.xy.util.OrderNoGenerator;
import com.lzu.xy.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final SnowflakeIdGenerator idGenerator;
    private final OrderNoGenerator orderNoGenerator;

    /**
     * 下单：校验商品 → 校验库存 → 扣库存 → 插订单，全部在同一个事务里。
     * 任何一步失败都会回滚，已扣的库存不会残留。
     */
    @Transactional
    public OrderVO create(OrderCreateDTO dto) {
        Product product = productRepository.findById(dto.getProductId())
                .filter(item -> item.getIsDeleted() == null || item.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(ResultCodeEnum.NOT_FOUND));

        if (product.getStatus() == null || product.getStatus() != ProductStatusEnum.ON.getValue()) {
            throw new BusinessException(ResultCodeEnum.PRODUCT_OFF_SHELF);
        }
        if (product.getStock() < dto.getQuantity()) {
            throw new BusinessException(ResultCodeEnum.INSUFFICIENT_STOCK);
        }

        LocalDateTime now = LocalDateTime.now();

        // 原子扣库存：并发下以受影响行数判断是否扣成功，不会出现超卖
        int deducted = productRepository.deductStock(product.getId(), dto.getQuantity(), now);
        if (deducted == 0) {
            throw new BusinessException(ResultCodeEnum.INSUFFICIENT_STOCK);
        }

        // 单价和总金额一律用数据库里的价格计算，不采信客户端传值
        int unitPrice = product.getPrice();
        long totalAmount = (long) unitPrice * dto.getQuantity();

        Order order = Order.builder()
                .id(idGenerator.nextId())
                .orderNo(orderNoGenerator.nextOrderNo())
                .productId(product.getId())
                .productTitle(product.getTitle())
                .quantity(dto.getQuantity())
                .unitPrice(unitPrice)
                .totalAmount(totalAmount)
                .buyerName(dto.getBuyerName())
                .buyerPhone(dto.getBuyerPhone())
                .address(dto.getAddress())
                .status(OrderStatusEnum.CREATED.getValue())
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(0)
                .build();

        return OrderVO.from(orderRepository.save(order));
    }

    @Transactional(readOnly = true)
    public OrderVO getByOrderNo(String orderNo) {
        return orderRepository.findByOrderNoAndIsDeleted(orderNo, 0)
                .map(OrderVO::from)
                .orElseThrow(() -> new BusinessException(ResultCodeEnum.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public PageVO<OrderVO> listByBuyerPhone(OrderQueryDTO query) {
        Pageable pageable = PageRequest.of(query.getPage() - 1, query.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Order> result = orderRepository.findByBuyerPhoneAndIsDeleted(
                query.getBuyerPhone(), 0, pageable);
        List<OrderVO> items = result.getContent().stream()
                .map(OrderVO::from)
                .toList();
        return PageVO.<OrderVO>builder()
                .total(result.getTotalElements())
                .page(query.getPage())
                .pageSize(query.getPageSize())
                .items(items)
                .build();
    }
}
