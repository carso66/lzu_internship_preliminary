package cn.edu.lzu.marketplace.service;

import cn.edu.lzu.marketplace.dto.CreateOrderRequest;
import cn.edu.lzu.marketplace.dto.OrderPageResponse;
import cn.edu.lzu.marketplace.dto.OrderResponse;

/**
 * 供订单控制器调用的业务接口，统一下单与订单查询的请求和返回类型。
 */
public interface OrderService {

    /**
     * 下单：校验商品与库存、原子扣减、服务端算价、生成订单号并落库（同一事务）。
     */
    OrderResponse create(CreateOrderRequest request);

    /**
     * 按订单号查询详情；不存在抛 40401。
     */
    OrderResponse getByOrderNo(String orderNo);

    /**
     * 按买家手机号分页查询，创建时间倒序，同一时间按订单主键倒序。
     */
    OrderPageResponse list(Integer page, Integer pageSize, String buyerPhone);
}
