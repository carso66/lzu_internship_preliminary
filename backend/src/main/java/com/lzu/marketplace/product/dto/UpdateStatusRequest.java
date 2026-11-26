package com.lzu.marketplace.product.dto;

/**
 * 上下架请求体，status 只允许 on / off，由 Service 校验。
 */
public record UpdateStatusRequest(String status) {
}
