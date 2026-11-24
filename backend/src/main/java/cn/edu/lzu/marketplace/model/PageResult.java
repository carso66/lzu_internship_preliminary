package cn.edu.lzu.marketplace.model;

import java.util.List;

/**
 * 通用分页查询结果：total 为满足筛选条件的总条数，items 为当前页数据。
 */
public record PageResult<T>(long total, List<T> items) {
}
