package com.lzu.marketplace.common;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 严格 JSON 类型校验：关闭两处隐式类型转换。
 *
 * <ul>
 *   <li>ALLOW_COERCION_OF_SCALARS：默认会把 {@code "1990"} 静默转成 1990，关闭后数值字段拒绝字符串。</li>
 *   <li>ACCEPT_FLOAT_AS_INT：显式关闭，确保 price/stock 拒绝 10.5、10.0 这类小数输入。</li>
 * </ul>
 * 违规输入都会抛 HttpMessageNotReadableException，由全局异常处理器返回 400/40001。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictJsonTypeCustomizer() {
        return builder -> builder.featuresToDisable(
                MapperFeature.ALLOW_COERCION_OF_SCALARS,
                DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    }
}
