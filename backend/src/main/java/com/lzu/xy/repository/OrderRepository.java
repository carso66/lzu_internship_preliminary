package com.lzu.xy.repository;

import com.lzu.xy.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository
        extends CrudRepository<Order, Long>,
        PagingAndSortingRepository<Order, Long>,
        JpaSpecificationExecutor<Order> {

    /** 按订单号查未删除的订单 */
    Optional<Order> findByOrderNoAndIsDeleted(String orderNo, Integer isDeleted);

    /** 按买家手机号分页查未删除的订单 */
    Page<Order> findByBuyerPhoneAndIsDeleted(String buyerPhone, Integer isDeleted, Pageable pageable);

    /**
     * 取某天已用的最大序号的订单号，用于生成当天的下一个序号。
     * 按长度倒序再按字典序倒序，保证 10000 排在 9999 之后。
     */
    @Query("""
            select o.orderNo from Order o
             where o.orderNo like concat(:prefix, '%')
             order by length(o.orderNo) desc, o.orderNo desc
            """)
    List<String> findLatestOrderNosByPrefix(@Param("prefix") String prefix, Pageable pageable);
}
