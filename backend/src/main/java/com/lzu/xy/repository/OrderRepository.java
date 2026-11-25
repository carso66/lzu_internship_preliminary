package com.lzu.xy.repository;

import com.lzu.xy.domain.Order;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface OrderRepository
        extends CrudRepository<Order, Long>,
        PagingAndSortingRepository<Order, Long>,
        JpaSpecificationExecutor<Order> {
}
