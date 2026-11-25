package com.example.marketplace.mapper;

import com.example.marketplace.domain.Order;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface OrderMapper {

    int insert(Order order);

    Order findById(long id);

    Order findByOrderNo(String orderNo);

    int markPaidIfCreated(@Param("id") long id, @Param("paidAt") OffsetDateTime paidAt);

    long countByBuyerPhone(@Param("buyerPhone") String buyerPhone);

    List<Order> findPageByBuyerPhone(
            @Param("buyerPhone") String buyerPhone,
            @Param("offset") long offset,
            @Param("limit") int limit
    );
}
