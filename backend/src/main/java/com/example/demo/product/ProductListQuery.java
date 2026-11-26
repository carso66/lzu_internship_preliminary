package com.example.demo.product;

import com.example.demo.common.ApiException;

/**
 * GET /api/products 的查询参数。
 *
 * 空串按「未传」处理（方便前端把空输入框直接拼进 URL）。
 * pageSize 超出 1~50 直接 400，而不是悄悄截断——否则调用方会以为拿到了全部数据。
 */
public record ProductListQuery(
		String keyword,
		String status,
		String category,
		int page,
		int pageSize) {

	public static final int DEFAULT_PAGE = 1;
	public static final int DEFAULT_PAGE_SIZE = 10;
	public static final int MAX_PAGE_SIZE = 50;

	public static ProductListQuery parse(String keyword, String status, String category, Integer page, Integer pageSize) {
		int resolvedPage = page == null ? DEFAULT_PAGE : page;
		if (resolvedPage < 1) {
			throw ApiException.badRequest("page 必须是不小于 1 的整数");
		}

		int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
		if (resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
			throw ApiException.badRequest("pageSize 需在 1~" + MAX_PAGE_SIZE + " 之间");
		}

		String resolvedStatus = blankToNull(status);
		if (resolvedStatus != null && !"on".equals(resolvedStatus) && !"off".equals(resolvedStatus)) {
			throw ApiException.badRequest("status 只能是 on 或 off");
		}

		return new ProductListQuery(blankToNull(keyword), resolvedStatus, blankToNull(category),
				resolvedPage, resolvedPageSize);
	}

	/** 偏移量按 long 计算，避免 page 很大时 int 溢出成负数。 */
	public long offset() {
		return (long) (page - 1) * pageSize;
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}
}
