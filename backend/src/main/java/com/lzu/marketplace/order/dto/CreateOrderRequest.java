package com.lzu.marketplace.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 下单请求体。
 *
 * <p>只接收"买什么、买多少、寄给谁"，<b>不接收价格</b>：
 * 单价与总金额一律由服务端按数据库里的商品价格计算，避免客户端伪造价格。</p>
 *
 * <p>quantity 必须是大于等于 1 的整数：0 和负数由 {@code @Min(1)} 拦下，
 * 字符串 {@code "2"} 和小数 {@code 2.5} 由 Jackson 严格类型配置拒绝，
 * 最终都返回 400 + 40001。</p>
 */
public record CreateOrderRequest(

        @NotNull(message = "不能为空")
        @Min(value = 1, message = "必须大于等于 1")
        Long productId,

        @NotNull(message = "不能为空")
        @Min(value = 1, message = "必须大于等于 1")
        Integer quantity,

        @NotBlank(message = "不能为空")
        @Size(max = 50, message = "长度不能超过 50")
        String buyerName,

        @NotBlank(message = "不能为空")
        @Size(max = 20, message = "长度不能超过 20")
        String buyerPhone,

        @NotBlank(message = "不能为空")
        @Size(max = 200, message = "长度不能超过 200")
        String address
) {
}
