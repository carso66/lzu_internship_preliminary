package com.lzu.marketplace.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * 商品列表的筛选与分页参数。keyword/status/category 可任意叠加；page、pageSize 缺省取默认值。
 * status 只接受 on/off；pageSize 限制在 1~50，避免一次返回过多数据。
 */
public record ProductQuery(
        String keyword,
        @Pattern(regexp = "on|off", message = "只能为 on 或 off") String status,
        String category,
        @Min(value = 1, message = "必须大于等于 1") Integer page,
        @Min(value = 1, message = "必须大于等于 1") @Max(value = 50, message = "必须小于等于 50") Integer pageSize
) {
    public ProductQuery {
        // 空字符串视同未传，避免 status= 之类被误判为非法值；分页参数缺省时应用默认值。
        keyword = blankToNull(keyword);
        status = blankToNull(status);
        category = blankToNull(category);
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
