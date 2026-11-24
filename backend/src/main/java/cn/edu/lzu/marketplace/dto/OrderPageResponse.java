package cn.edu.lzu.marketplace.dto;

import java.util.List;

/**
 * 订单列表响应 data：total 为某手机号下的订单总条数，items 为当前页数组。
 * 与商品列表（L0-3）保持一致的分页结构。
 */
public record OrderPageResponse(long total, int page, int pageSize, List<OrderResponse> items) {
}
