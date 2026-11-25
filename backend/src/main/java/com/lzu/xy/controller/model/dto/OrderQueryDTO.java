package com.lzu.xy.controller.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 订单列表查询条件。
 */
@Data
public class OrderQueryDTO {

    /** 按买家手机号查；必填 */
    @NotBlank(message = "不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "格式不正确，应为11位手机号")
    private String buyerPhone;

    @Min(value = 1, message = "不能小于1")
    private Integer page = 1;

    @Min(value = 1, message = "不能小于1")
    @Max(value = 50, message = "不能大于50")
    private Integer pageSize = 10;
}
