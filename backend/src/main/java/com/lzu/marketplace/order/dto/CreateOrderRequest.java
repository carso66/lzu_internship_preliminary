package com.lzu.marketplace.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 下单请求体（官方 §5 L1-6）。
 *
 * <p><b>刻意没有价格字段</b>：{@code unitPrice} / {@code totalAmount} 只能由服务端
 * 根据数据库中的商品价格计算（题目 L1-6 第 5 条）。客户端若在 JSON 里多传
 * {@code price} / {@code unitPrice} 等字段，Jackson 默认忽略未知属性，不会影响计算。
 *
 * <p>类型严格性：{@code quantity} 传 {@code "2"}（字符串）、{@code 2.5}（小数）、
 * {@code null} 都会在反序列化或校验阶段被拒，返回 400 + 40001，
 * 依赖的是 {@code spring.jackson.mapper.allow-coercion-of-scalars=false}
 * 与 {@code accept-float-as-int=false}（见 application.yml）。
 *
 * <p>不设 quantity 上限：题目只要求「≥1 的整数」，超过库存的数量是业务失败（409）
 * 而不是参数格式错误（400）。整数超出 int 范围时由 Jackson 拦截为 400。
 *
 * @param productId  商品 id，必填
 * @param quantity   购买数量，必填，≥1
 * @param buyerName  买家姓名，必填，1~64 字符
 * @param buyerPhone 买家手机号，必填，1~20 字符
 * @param address    收货地址，必填，1~255 字符
 */
public record CreateOrderRequest(

        @NotNull(message = "不能为空")
        @Positive(message = "必须是正整数")
        Long productId,

        @NotNull(message = "不能为空")
        @Min(value = 1, message = "必须是大于等于 1 的整数")
        Integer quantity,

        @NotBlank(message = "不能为空")
        @Size(max = 64, message = "长度不能超过 64 个字符")
        String buyerName,

        @NotBlank(message = "不能为空")
        @Size(max = 20, message = "长度不能超过 20 个字符")
        String buyerPhone,

        @NotBlank(message = "不能为空")
        @Size(max = 255, message = "长度不能超过 255 个字符")
        String address) {
}
