package com.lzu.xy.controller.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发布商品请求。
 */
@Data
public class ProductCreateDTO {

    @NotBlank(message = "不能为空")
    @Size(max = 50, message = "长度必须在1~50个字符之间")
    private String title;

    /** 单价，单位：分，必须为正整数 */
    @NotNull(message = "不能为空")
    @Positive(message = "必须为正整数（单位：分）")
    private Integer price;

    @NotNull(message = "不能为空")
    @Min(value = 0, message = "必须在0~999之间")
    @Max(value = 999, message = "必须在0~999之间")
    private Integer stock;

    @NotBlank(message = "不能为空")
    @Size(max = 50, message = "长度不能超过50个字符")
    private String category;

    @Size(max = 200, message = "长度不能超过200个字符")
    private String description;

    @NotBlank(message = "不能为空")
    @Size(max = 64, message = "长度不能超过64个字符")
    private String sellerName;
}
