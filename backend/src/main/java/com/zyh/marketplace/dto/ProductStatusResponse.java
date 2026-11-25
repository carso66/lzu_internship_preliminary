package com.zyh.marketplace.dto;

/**
 * PATCH /api/products/{id}/status 的响应数据：只含 id 与 status（题目规定）。
 */
public record ProductStatusResponse(Long id, String status) {
}
