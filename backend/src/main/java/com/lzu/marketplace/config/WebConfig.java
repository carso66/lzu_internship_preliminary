package com.lzu.marketplace.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 明确 JSON 响应的字符集与内容协商。
 *
 * <p>Spring 默认返回 {@code Content-Type: application/json}（无 charset），字节实际是 UTF-8。
 * 部分客户端会按本地编码猜测，导致中文 message 显示乱码。这里显式声明
 * {@code application/json;charset=UTF-8}。
 *
 * <p><b>关键</b>：只修改已有转换器的字符集，<b>不新建 ObjectMapper</b>。
 * 新建的 {@code Jackson2ObjectMapperBuilder} 不会继承 {@code spring.jackson.*} 配置，
 * 会静默关闭严格类型校验（allow-coercion-of-scalars / accept-float-as-int）。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // extendMessageConverters 拿到的是 Spring 已按 application.yml 建好的转换器实例，
        // 只调整 charset / supported media type，ObjectMapper 保持原样。
        for (HttpMessageConverter<?> converter : converters) {
            if (converter instanceof MappingJackson2HttpMessageConverter jackson) {
                jackson.setDefaultCharset(StandardCharsets.UTF_8);
                List<MediaType> types = new ArrayList<>(jackson.getSupportedMediaTypes());
                if (!types.contains(MediaType.APPLICATION_JSON)) {
                    types.add(MediaType.APPLICATION_JSON);
                }
                types.removeIf(t -> t.getCharset() != null);
                types.add(new MediaType("application", "json", StandardCharsets.UTF_8));
                jackson.setSupportedMediaTypes(types);
            }
        }
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        // 本项目只有 JSON 一种表示，固定下来，避免 Accept 协商出 406
        configurer.defaultContentType(MediaType.APPLICATION_JSON);
    }
}
