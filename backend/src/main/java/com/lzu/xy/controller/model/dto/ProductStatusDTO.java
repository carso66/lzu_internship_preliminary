package com.lzu.xy.controller.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 修改商品上下架状态请求。
 */
@Data
public class ProductStatusDTO {

    @NotBlank(message = "不能为空")
    @Pattern(regexp = "on|off", message = "只能是 on 或 off")
    private String status;
}
