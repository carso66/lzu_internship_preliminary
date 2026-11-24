package cn.edu.lzu.marketplace.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON 反序列化严格化：整数字段（int/Integer/long/Long 等）不再接受
 * 隐式转换，覆盖题目中 price/stock/quantity 这类字段的类型校验要求：
 *
 * <ul>
 *   <li>"1990"（字符串形式的数字）→ 拒绝，默认行为会转成 1990</li>
 *   <li>1990.5 / 1990.0（小数）→ 拒绝，默认行为会截断成 1990</li>
 *   <li>""（空串）→ 拒绝，默认行为可能给 0</li>
 * </ul>
 *
 * <p>仅影响 JSON 请求体；查询参数本来就是字符串（如 ?page=1），
 * 由 Spring 的类型转换处理，不受此配置影响，非法值走
 * MethodArgumentTypeMismatchException → 40001。
 *
 * <p>拒绝时抛 MismatchedInputException，被 Spring 包装为
 * HttpMessageNotReadableException，由 GlobalExceptionHandler 转 400 + 40001。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictIntegerCoercionCustomizer() {
        return builder -> builder.postConfigurer(mapper ->
                mapper.coercionConfigFor(LogicalType.Integer)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail));
    }
}
