package com.zyh.marketplace.dto;

/**
 * Mapper 层分页参数。offset 用 long 计算，Service 层已校验不会溢出 int/total。
 */
public record ProductPageCriteria(
        String keyword,
        String status,
        String category,
        int offset,
        int limit
) {
}
