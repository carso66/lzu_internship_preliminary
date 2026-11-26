package com.lzu.marketplace.common;

import java.util.List;

/**
 * 通用分页结果，作为 ApiResponse 的 data 返回。
 *
 * @param total    筛选后的总条数（不是当前页数量）
 * @param page     当前页码
 * @param pageSize 每页条数
 * @param items    当前页数据
 */
public record PageResult<T>(long total, int page, int pageSize, List<T> items) {
}
