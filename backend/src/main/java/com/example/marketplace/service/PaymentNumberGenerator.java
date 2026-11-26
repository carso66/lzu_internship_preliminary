package com.example.marketplace.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/**
 * 生成带日期前缀和随机后缀的支付号，格式为 PAYyyyyMMdd + 12 位十六进制字符。
 */
@Component
public class PaymentNumberGenerator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    public String generate() {
        String suffix = UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 12)
                .toUpperCase(Locale.ROOT);
        return "PAY" + LocalDate.now().format(DATE_FORMATTER) + suffix;
    }
}
