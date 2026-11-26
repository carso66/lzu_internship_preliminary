package com.lzu.marketplace.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 更新商品上下架状态的请求体（官方 §4 L0-5）。
 *
 * <p>{@code status} 只能是 {@code on} / {@code off}，否则 400 + 40001。
 *
 * <p>三点刻意设计：
 * <ul>
 *   <li>用 {@code @NotBlank} + {@code @Pattern} 而非自定义枚举类型：Jackson 若把取值解析成
 *       枚举，非法值会在反序列化阶段抛异常，message 是框架生成的，说不清哪个字段错了；
 *       用 String + 正则，校验失败的 message 由我们控制。</li>
 *   <li>用 {@code @Pattern} 而不是在 Service 里 if-else：校验规则待在 DTO 上，
 *       Controller 和测试都能直接看到约束。</li>
 *   <li>包裹类型无关紧要（String 本身就是引用类型），但**不能**允许 null：
 *       {@code @NotBlank} 同时拦截 null 与空白串。</li>
 * </ul>
 *
 * @param status 目标状态，只接受 on / off
 */
public record UpdateProductStatusRequest(

        @NotBlank(message = "不能为空")
        @Pattern(regexp = "^(on|off)$", message = "只能是 on 或 off")
        String status) {
}
