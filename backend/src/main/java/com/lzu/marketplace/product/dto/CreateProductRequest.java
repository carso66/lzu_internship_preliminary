package com.lzu.marketplace.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 发布商品的请求体（官方 §4 L0-2）。
 *
 * <p>所有数值字段一律用<b>包装类型</b>：JSON {@code null} 落到原始类型会被静默变成 0，
 * 用包装类型才能让 {@code @NotNull} 准确区分「没传」与「传了 0」。
 *
 * <p>类型严格性（字符串 {@code "1990"}、小数 {@code 19.9}、布尔 {@code true} 一律拒绝）
 * 由 {@code spring.jackson.mapper.allow-coercion-of-scalars=false} 与
 * {@code spring.jackson.deserialization.accept-float-as-int=false} 在反序列化阶段拦截，
 * 不依赖这里的注解。
 *
 * <p>取值范围说明：题目只要求「字段长度，类型等基础问题」，具体数值由本项目自定，
 * 历史记录见 docs/P01-需求分析.md §7。
 *
 * @param title       标题，必填，1~50 字符
 * @param price       单价，必填，单位「分」，1~999999999
 * @param stock       库存，必填，0~999
 * @param category    分类，必填，1~32 字符，不限定枚举
 * @param description 描述，可选，最长 200 字符
 * @param sellerName  卖家名称，必填，1~64 字符
 */
public record CreateProductRequest(

        @NotBlank(message = "不能为空")
        @Size(max = 50, message = "长度不能超过 50 个字符")
        String title,

        @NotNull(message = "不能为空")
        @Min(value = 1, message = "必须是大于 0 的整数（单位：分）")
        @Max(value = 999_999_999L, message = "不能超过 999999999 分")
        Long price,

        @NotNull(message = "不能为空")
        @Min(value = 0, message = "不能为负数")
        @Max(value = 999, message = "不能超过 999")
        Integer stock,

        @NotBlank(message = "不能为空")
        @Size(max = 32, message = "长度不能超过 32 个字符")
        String category,

        @Size(max = 200, message = "长度不能超过 200 个字符")
        String description,

        @NotBlank(message = "不能为空")
        @Size(max = 64, message = "长度不能超过 64 个字符")
        String sellerName) {
}
