package com.example.marketplace.service;

import com.example.marketplace.domain.Order;
import com.example.marketplace.domain.Product;
import com.example.marketplace.dto.CreateOrderRequest;
import com.example.marketplace.exception.InsufficientStockException;
import com.example.marketplace.exception.ProductNotFoundException;
import com.example.marketplace.exception.ProductOffSaleException;
import com.example.marketplace.mapper.OrderMapper;
import com.example.marketplace.mapper.ProductMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private final ProductMapper productMapper;
    private final OrderMapper orderMapper;
    private final OrderNumberGenerator orderNumberGenerator;

    public OrderService(
            ProductMapper productMapper,
            OrderMapper orderMapper,
            OrderNumberGenerator orderNumberGenerator
    ) {
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
        this.orderNumberGenerator = orderNumberGenerator;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Order create(CreateOrderRequest request) {
        int updatedRows = productMapper.decreaseStockIfAvailable(request.productId(), request.quantity());
        if (updatedRows == 0) {
            throwStockError(request.productId(), request.quantity());
        }

        Product product = productMapper.findById(request.productId());
        Order order = new Order();
        order.setOrderNo(orderNumberGenerator.generate());
        order.setProductId(product.getId());
        order.setProductTitle(product.getTitle());
        order.setQuantity(request.quantity());
        order.setUnitPrice(product.getPrice());
        order.setTotalAmount(Math.multiplyExact(product.getPrice(), request.quantity()));
        order.setBuyerName(request.buyerName());
        order.setBuyerPhone(request.buyerPhone());
        order.setAddress(request.address());
        order.setStatus("created");

        orderMapper.insert(order);
        return orderMapper.findById(order.getId());
    }

    private void throwStockError(long productId, int quantity) {
        Product product = productMapper.findById(productId);
        if (product == null) {
            throw new ProductNotFoundException(productId);
        }
        if ("off".equals(product.getStatus())) {
            throw new ProductOffSaleException(productId);
        }
        throw new InsufficientStockException(productId);
    }
}
