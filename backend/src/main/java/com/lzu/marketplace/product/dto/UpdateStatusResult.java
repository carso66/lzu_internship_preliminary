package com.lzu.marketplace.product.dto;

/**
 * 上下架结果，只回显 id 和新的 status。
 */
public record UpdateStatusResult(Long id, String status) {
}
