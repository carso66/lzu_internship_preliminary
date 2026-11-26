package com.example.demo.order;

import com.example.demo.common.ApiException;

/**
 * GET /api/orders 的查询参数。
 *
 * buyerPhone 必填：这个接口的语义就是「查某个手机号的订单」，
 * 不给手机号就返回全表订单既没有业务意义，也会把别人的订单暴露出去。
 */
public record OrderListQuery(String buyerPhone, int page, int pageSize) {

	public static final int DEFAULT_PAGE = 1;
	public static final int DEFAULT_PAGE_SIZE = 10;
	public static final int MAX_PAGE_SIZE = 50;

	public static OrderListQuery parse(String buyerPhone, Integer page, Integer pageSize) {
		String resolvedPhone = buyerPhone == null ? "" : buyerPhone.trim();
		if (resolvedPhone.isEmpty()) {
			throw ApiException.badRequest("buyerPhone 不能为空");
		}

		int resolvedPage = page == null ? DEFAULT_PAGE : page;
		if (resolvedPage < 1) {
			throw ApiException.badRequest("page 必须是不小于 1 的整数");
		}

		int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
		if (resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
			throw ApiException.badRequest("pageSize 需在 1~" + MAX_PAGE_SIZE + " 之间");
		}

		return new OrderListQuery(resolvedPhone, resolvedPage, resolvedPageSize);
	}

	/** 偏移量按 long 计算，避免 page 很大时 int 溢出成负数。 */
	public long offset() {
		return (long) (page - 1) * pageSize;
	}
}
