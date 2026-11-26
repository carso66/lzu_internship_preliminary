package com.lzu.marketplace.config;

import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

/**
 * Jackson 序列化定制。
 *
 * <p><b>用 Module 而不是新建 ObjectMapper</b>：Spring Boot 会把容器里的
 * {@code com.fasterxml.jackson.databind.Module} bean 自动注册到它构建的
 * {@code ObjectMapper} 上，因此 {@code spring.jackson.*} 的严格类型配置
 * （allow-coercion-of-scalars / accept-float-as-int）不受影响。
 *
 * <p>若改用 {@code Jackson2ObjectMapperBuilder#build()} 手工新建，会丢掉上述配置——
 * 这个坑在 P04 已经踩过一次。
 */
@Configuration
public class JacksonConfig {

    @Bean
    public SimpleModule millisecondLocalDateTimeModule() {
        SimpleModule module = new SimpleModule("MillisecondLocalDateTimeModule");
        module.addSerializer(LocalDateTime.class, new MillisecondLocalDateTimeSerializer());
        return module;
    }
}
