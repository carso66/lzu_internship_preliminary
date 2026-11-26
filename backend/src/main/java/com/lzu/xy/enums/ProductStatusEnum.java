package com.lzu.xy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * 商品上下架状态：数据库存 1/0，接口暴露 on/off。
 */
@Getter
@AllArgsConstructor
public enum ProductStatusEnum {

    ON(1, "on"),
    OFF(0, "off");

    private final int value;
    private final String label;

    public static Optional<ProductStatusEnum> fromLabel(String label) {
        return Arrays.stream(values())
                .filter(status -> status.label.equals(label))
                .findFirst();
    }

    public static String labelOf(int value) {
        return Arrays.stream(values())
                .filter(status -> status.value == value)
                .map(ProductStatusEnum::getLabel)
                .findFirst()
                .orElse(OFF.label);
    }
}
