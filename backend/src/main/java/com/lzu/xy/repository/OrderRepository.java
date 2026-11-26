package com.lzu.xy.repository;

import com.lzu.xy.domain.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
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

    /**
     * 原子把订单从「待支付」改成「已支付」，同时写入支付时间。
     * 返回 0 表示订单已不是待支付状态（已被支付或已取消），用于支付幂等判断。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Order o
               set o.status = :paidStatus,
                   o.paidAt = :paidAt,
                   o.updatedAt = :paidAt
             where o.id = :id
               and o.status = :createdStatus
               and o.isDeleted = 0
            """)
    int markPaid(@Param("id") Long id,
                 @Param("createdStatus") Integer createdStatus,
                 @Param("paidStatus") Integer paidStatus,
                 @Param("paidAt") LocalDateTime paidAt);
}
