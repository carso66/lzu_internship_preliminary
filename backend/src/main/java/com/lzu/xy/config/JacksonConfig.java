package com.lzu.xy.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * 收紧 JSON 类型转换规则：
 * <ul>
 *   <li>字符串 "2" 不允许自动转成整数 2（下单 quantity 必须传数字）</li>
 *   <li>小数 1.5 不允许自动截断成整数 1（金额/库存必须是整数）</li>
 *   <li>布尔值不允许转成 0/1</li>
 * </ul>
 * 违反时由 GlobalExceptionHandler 统一返回 400 + 40001，并指出是哪个字段。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer strictCoercionCustomizer() {
        return builder -> builder
                .withCoercionConfig(LogicalType.Integer, config -> {
                    config.setCoercion(CoercionInputShape.String, CoercionAction.Fail);
                    config.setCoercion(CoercionInputShape.Float, CoercionAction.Fail);
                    config.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
                })
                .withCoercionConfig(LogicalType.Float, config ->
                        config.setCoercion(CoercionInputShape.String, CoercionAction.Fail))
                .withCoercionConfig(LogicalType.Boolean, config ->
                        config.setCoercion(CoercionInputShape.String, CoercionAction.Fail));
    }
}
