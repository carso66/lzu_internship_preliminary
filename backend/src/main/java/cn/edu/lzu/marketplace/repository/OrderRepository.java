package cn.edu.lzu.marketplace.repository;

import cn.edu.lzu.marketplace.model.Order;
import cn.edu.lzu.marketplace.model.PageResult;

import java.util.Optional;

/**
 * 订单数据访问接口。
 */
public interface OrderRepository {

    /**
     * 插入一条订单，返回自增主键。orderNo 列由调用方填入临时占位值，
     * 插入拿到主键后由 {@link #updateOrderNo(Long, String)} 回填正式编号。
     */
    Long insert(Order order);

    /**
     * 回填正式订单号（由自增主键派生），返回受影响行数。
     */
    int updateOrderNo(Long id, String orderNo);

    /**
     * 按订单号查询，不存在返回空 Optional。
     */
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 按买家手机号分页查询，创建时间倒序。
     *
     * @param offset 起始偏移量（从 0 开始），用 long 防止 (page-1)*pageSize 整数溢出
     * @param limit  每页条数
     */
    PageResult<Order> findPage(long offset, int limit, String buyerPhone);
}
