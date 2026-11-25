package com.lzu.marketplace.payment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * 支付编号由数据库序列生成，保证并发请求拿到的序号不重复。
 * 回滚后序号会出现空洞，但支付编号只要求唯一，不要求连续。
 */
@Component
public class PaymentNumberGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final JdbcTemplate jdbc;

    public PaymentNumberGenerator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String next() {
        Long sequence = jdbc.queryForObject("SELECT NEXT VALUE FOR payment_no_seq", Long.class);
        if (sequence == null) {
            throw new IllegalStateException("支付编号序列未返回有效值");
        }
        return "PAY" + LocalDate.now(ZoneOffset.UTC).format(DATE_FORMAT)
                + String.format(Locale.ROOT, "%04d", sequence);
    }
}
