package com.lzu.marketplace.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 将 {@link LocalDateTime} 序列化为<b>毫秒精度</b>的 ISO-8601 字符串。
 *
 * <p>为什么需要：H2 的 {@code TIMESTAMP} 列保存微秒（{@code 2026-11-24T12:02:46.345939}），
 * Jackson 默认原样输出。前后端对接时常见的问题是：
 * <ul>
 *   <li>JS 的 {@code Date} 只支持毫秒，解析微秒会丢精度或报错；</li>
 *   <li>同一时间在不同接口返回不同精度，客户端比较字符串会不相等。</li>
 * </ul>
 *
 * <p>统一截断到毫秒（{@code 2026-11-24T12:02:46.345}），不四舍五入——
 * 时间戳的语义是「不晚于真实时刻」，向上取整会造出未来时间。
 */
public class MillisecondLocalDateTimeSerializer extends JsonSerializer<LocalDateTime> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");

    @Override
    public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider)
            throws IOException {
        gen.writeString(value.truncatedTo(ChronoUnit.MILLIS).format(FORMATTER));
    }
}
