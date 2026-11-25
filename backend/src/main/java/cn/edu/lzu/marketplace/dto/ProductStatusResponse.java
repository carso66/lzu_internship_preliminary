package cn.edu.lzu.marketplace.dto;

/**
 * 商品上下架响应 DTO，返回变更目标的标识和更新后的状态。
 */
public record ProductStatusResponse(Long id, String status) {
}
