package com.lzu.marketplace.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 订单列表必须按买家手机号精确查询，避免无鉴权场景下直接导出全部订单。 */
public class OrderListQuery {

    @NotBlank(message = "不能为空")
    @Size(max = 20, message = "长度不能超过 20")
    private String buyerPhone;

    @NotNull(message = "不能为空")
    @Min(value = 1, message = "必须大于等于 1")
    private Integer page = 1;

    @NotNull(message = "不能为空")
    @Min(value = 1, message = "必须在 1 到 50 之间")
    @Max(value = 50, message = "必须在 1 到 50 之间")
    private Integer pageSize = 10;

    public String getBuyerPhone() {
        return buyerPhone;
    }

    public void setBuyerPhone(String buyerPhone) {
        this.buyerPhone = buyerPhone == null ? null : buyerPhone.strip();
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
}
