package cn.edu.lzu.marketplace.dto;

/**
 * PATCH 上下架接口的响应 data：仅 id 与 status。
 */
public record ProductStatusResponse(Long id, String status) {
}
