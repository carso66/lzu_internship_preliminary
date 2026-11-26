package com.lzu.xy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 订单状态：数据库存 0/1/2，接口对外暴露 created / paid / cancelled。
 */
@Getter
@AllArgsConstructor
public enum OrderStatusEnum {

    CREATED(0, "created"),
    PAID(1, "paid"),
    CANCELLED(2, "cancelled");

    private final int value;
    private final String label;

    public static Optional<OrderStatusEnum> fromLabel(String label) {
        return Arrays.stream(values())
                .filter(status -> status.label.equals(label))
                .findFirst();
    }

    public static String labelOf(Integer value) {
        if (value == null) {
            return CREATED.label;
        }
        return Arrays.stream(values())
                .filter(status -> status.value == value)
                .map(OrderStatusEnum::getLabel)
                .findFirst()
                .orElse(CREATED.label);
    }
}
