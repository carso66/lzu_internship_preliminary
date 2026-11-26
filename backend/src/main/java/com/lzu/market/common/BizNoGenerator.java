package com.lzu.market.common;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 业务单号生成器：前缀 + 当天日期 + 6 位序号，如 SO20261124000001。
 * 进程内自增，跨天自动归零；synchronized 保证「取号 + 换天重置」是原子的。
 */
@Component
public class BizNoGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    private final AtomicLong seq = new AtomicLong();
    private LocalDate currentDay;

    public synchronized String next(String prefix) {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDay)) {
            currentDay = today;
            seq.set(0);
        }
        return prefix + today.format(DAY) + String.format("%06d", seq.incrementAndGet());
    }
}
