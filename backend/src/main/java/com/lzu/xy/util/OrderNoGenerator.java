package com.lzu.xy.util;

import com.lzu.xy.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 订单号生成器：SO + yyyyMMdd + 当日序号（4 位起）。
 * 例如 SO202611190001。
 * <p>
 * 序号在 JVM 内用 synchronized 递增，启动/跨天时从数据库当日最大订单号续上，
 * 保证同一实例并发下单也不会撞号。
 */
@Component
@RequiredArgsConstructor
public class OrderNoGenerator {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final OrderRepository orderRepository;

    private LocalDate currentDate;
    private int currentSequence;

    public synchronized String nextOrderNo() {
        LocalDate today = LocalDate.now();
        if (!today.equals(currentDate)) {
            currentDate = today;
            currentSequence = loadSequence(today);
        }
        currentSequence++;
        return "SO" + today.format(DATE_FORMAT) + String.format("%04d", currentSequence);
    }

    private int loadSequence(LocalDate date) {
        String prefix = "SO" + date.format(DATE_FORMAT);
        List<String> latest = orderRepository.findLatestOrderNosByPrefix(prefix, PageRequest.of(0, 1));
        return latest.stream()
                .findFirst()
                .map(orderNo -> orderNo.substring(prefix.length()))
                .flatMap(this::parseSuffix)
                .orElse(0);
    }

    private Optional<Integer> parseSuffix(String suffix) {
        try {
            return Optional.of(Integer.parseInt(suffix));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
