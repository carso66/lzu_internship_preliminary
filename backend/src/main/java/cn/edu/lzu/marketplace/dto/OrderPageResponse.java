package cn.edu.lzu.marketplace.dto;

import java.util.List;

/**
 * 订单列表响应 data：total 为某手机号下的订单总条数，items 为当前页数组。
 * 由订单服务将查询结果映射为响应 DTO，并附上当前页码和页大小。
 */
public record OrderPageResponse(long total, int page, int pageSize, List<OrderResponse> items) {
}
