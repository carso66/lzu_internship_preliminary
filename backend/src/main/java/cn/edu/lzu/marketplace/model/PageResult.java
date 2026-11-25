package cn.edu.lzu.marketplace.model;

import java.util.List;

/**
 * 数据访问层的分页结果，包含匹配总数和当前页数据，由业务层补充页码并转换为响应 DTO；封装分页查询结果。
 */
public record PageResult<T>(long total, List<T> items) {
}
