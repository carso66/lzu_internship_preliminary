package cn.edu.lzu.marketplace.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置 Jackson 对 JSON 整数字段的类型约束，供商品价格、库存和购买数量等请求字段使用。
 *
 * <p>请求体先由 Jackson 反序列化，再由 Bean Validation 检查必填和取值范围。
 * 查询参数继续由 Spring MVC 转换与校验，解析失败由全局异常处理器统一映射。
 */
@Configuration
public class JacksonConfig {

    // 在 Spring Boot 构建 ObjectMapper 时禁用字符串、小数和空串到整数的隐式转换。
    // 例如 "1990" 和 1990.0 均会被拒绝，避免字符串转换或小数截断后通过范围校验。
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer strictIntegerCoercionCustomizer() {
        return builder -> builder.postConfigurer(mapper ->
                mapper.coercionConfigFor(LogicalType.Integer)
                        .setCoercion(CoercionInputShape.String, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                        .setCoercion(CoercionInputShape.EmptyString, CoercionAction.Fail));
    }
}
