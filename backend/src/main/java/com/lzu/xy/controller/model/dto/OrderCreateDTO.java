package com.lzu.xy.controller.model.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 下单请求。单价和总金额由服务端按数据库价格计算，客户端传了也会被忽略。
 */
@Data
public class OrderCreateDTO {

    @NotNull(message = "不能为空")
    private Long productId;

    /** 购买数量，必须是 ≥1 的整数；不接受字符串 "2"、小数和 0/-1 */
    @NotNull(message = "不能为空")
    @Min(value = 1, message = "必须是大于等于1的整数")
    private Integer quantity;

    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过64个字符")
    private String buyerName;

    @NotBlank(message = "不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "格式不正确，应为11位手机号")
    private String buyerPhone;

    @NotBlank(message = "不能为空")
    @Size(max = 255, message = "长度不能超过255个字符")
    private String address;
}
