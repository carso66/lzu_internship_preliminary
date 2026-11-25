package com.zyh.marketplace.common;

/**
 * 商品上下架状态，与 schema.sql 中 chk_products_status 的取值保持一致。
 */
public final class ProductStatus {

    public static final String ON = "on";
    public static final String OFF = "off";

    private ProductStatus() {
    }

    public static boolean isValid(String status) {
        return ON.equals(status) || OFF.equals(status);
    }
}
