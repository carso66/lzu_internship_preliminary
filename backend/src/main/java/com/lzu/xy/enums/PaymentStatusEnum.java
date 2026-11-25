package com.lzu.xy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 支付单状态：数据库存 0/1/2，接口对外暴露 processing / paid / failed。
 */
@Getter
@AllArgsConstructor
public enum PaymentStatusEnum {

    PROCESSING(0, "processing"),
    PAID(1, "paid"),
    FAILED(2, "failed");

    private final int value;
    private final String label;

    public static String labelOf(Integer value) {
        if (value == null) {
            return PROCESSING.label;
        }
        return Arrays.stream(values())
                .filter(status -> status.value == value)
                .map(PaymentStatusEnum::getLabel)
                .findFirst()
                .orElse(PROCESSING.label);
    }
}
