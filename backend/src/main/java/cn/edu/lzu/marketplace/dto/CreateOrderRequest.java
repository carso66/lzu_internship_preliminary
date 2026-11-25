package cn.edu.lzu.marketplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 下单请求 DTO，定义 Controller 接受的商品、购买数量和买家信息。
 *
 * <p>quantity 为 Integer，要求 ≥1；配合 JacksonConfig 的严格整数反序列化，
 * 字符串形式的数字（"2"）、小数（2.5 / 2.0）、空串都会被拒绝，缺失走 @NotNull，
 * 0 / 负数走 @Min(1)。价格不接受客户端传入，由服务端根据数据库商品价格计算。
 */
public record CreateOrderRequest(
        // 购买对象和数量
        @NotNull(message = "商品ID不能为空")
        Long productId,

        @NotNull(message = "购买数量不能为空")
        @Min(value = 1, message = "购买数量必须为不小于1的整数")
        Integer quantity,

        // 买家及收货信息
        @NotBlank(message = "买家姓名不能为空")
        @Size(max = 50, message = "买家姓名长度不能超过50")
        String buyerName,

        @NotBlank(message = "买家手机号不能为空")
        @Size(max = 20, message = "买家手机号长度不能超过20")
        String buyerPhone,

        @NotBlank(message = "收货地址不能为空")
        @Size(max = 200, message = "收货地址长度不能超过200")
        String address) {
}
