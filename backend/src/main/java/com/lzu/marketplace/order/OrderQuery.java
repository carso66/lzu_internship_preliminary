package com.lzu.marketplace.order;

import jakarta.validation.constraints.*;

public record OrderQuery(
        @NotNull(message = "不能为空") @Pattern(regexp = "[0-9]{11}", message = "必须为11位数字") String buyerPhone,
        @Min(value = 1, message = "必须至少为1") Integer page,
        @Min(value = 1, message = "必须至少为1") @Max(value = 50, message = "最多50") Integer pageSize) {
    public OrderQuery {
        page = page == null ? 1 : page;
        pageSize = pageSize == null ? 10 : pageSize;
    }
}
