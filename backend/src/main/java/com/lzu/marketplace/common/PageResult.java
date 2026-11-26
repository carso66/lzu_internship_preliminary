package com.lzu.marketplace.common;

import java.util.List;

/**
 * 分页结果外层，对应官方 §4 L0-3 要求的 {@code total / page / pageSize / items}。
 *
 * <p>{@code total} 是<b>筛选后</b>的总条数，不是当前页条数。
 *
 * <p>放在 common 下是因为订单列表（L1-8）也需要同样的结构，避免两处各写一套。
 *
 * @param total    筛选后的总条数
 * @param page     当前页码，从 1 开始
 * @param pageSize 每页条数
 * @param items    当前页数据，无数据时为空数组（不是 null）
 */
public record PageResult<T>(long total, int page, int pageSize, List<T> items) {

    public static <T> PageResult<T> of(long total, int page, int pageSize, List<T> items) {
        return new PageResult<>(total, page, pageSize, items == null ? List.of() : items);
    }
}
