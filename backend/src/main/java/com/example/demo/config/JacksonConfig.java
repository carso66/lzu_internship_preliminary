package com.example.demo.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

@Configuration
public class JacksonConfig {

    @Bean
    public JsonMapperBuilderCustomizer strictJsonTypes() {
        return builder -> {

            // 整数字段不接受字符串、小数、布尔值
            builder.withCoercionConfig(LogicalType.Integer, config -> {
                config.setCoercion(
                        CoercionInputShape.String, CoercionAction.Fail);
                config.setCoercion(
                        CoercionInputShape.EmptyString, CoercionAction.Fail);
                config.setCoercion(
                        CoercionInputShape.Float, CoercionAction.Fail);
                config.setCoercion(
                        CoercionInputShape.Boolean, CoercionAction.Fail);
            });

            // 文本字段不接受数字、布尔值
            builder.withCoercionConfig(LogicalType.Textual, config -> {
                config.setCoercion(
                        CoercionInputShape.Integer, CoercionAction.Fail);
                config.setCoercion(
                        CoercionInputShape.Float, CoercionAction.Fail);
                config.setCoercion(
                        CoercionInputShape.Boolean, CoercionAction.Fail);
            });
        };
    }
}