package com.lzu.marketplace.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 查询参数缺省时使用默认分页；显式传入非法值时由 MVC 校验。 */
public class ProductListQuery {

    @Size(max = 120, message = "长度不能超过 120")
    private String keyword;

    @Pattern(regexp = "on|off", message = "只能为 on 或 off")
    private String status;

    @Size(max = 50, message = "长度不能超过 50")
    private String category;

    @NotNull(message = "不能为空")
    @Min(value = 1, message = "必须大于等于 1")
    private Integer page = 1;

    @NotNull(message = "不能为空")
    @Min(value = 1, message = "必须在 1 到 50 之间")
    @Max(value = 50, message = "必须在 1 到 50 之间")
    private Integer pageSize = 10;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = normalizeFilter(keyword);
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        // 与 keyword/category 保持一致：空白串视为未传，表示不过滤状态。
        this.status = normalizeFilter(status);
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = normalizeFilter(category);
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    public long offset() {
        return (page.longValue() - 1) * pageSize;
    }

    private static String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
