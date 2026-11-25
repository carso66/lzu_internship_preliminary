package com.example.marketplace.mapper;

import com.example.marketplace.domain.Order;

public interface OrderMapper {

    int insert(Order order);

    Order findById(long id);

    Order findByOrderNo(String orderNo);
}
