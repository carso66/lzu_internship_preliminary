package com.zyh.marketplace.common;

import java.util.List;

/**
 * 分页结果。total 是**筛选后的总条数**（与当前页无关），items 只包含当前页数据。
 */
public record PageResult<T>(long total, int page, int pageSize, List<T> items) {
}
