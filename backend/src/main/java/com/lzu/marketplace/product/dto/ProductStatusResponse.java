package com.lzu.marketplace.product.dto;

import com.lzu.marketplace.product.Product;

/**
 * 上下架接口的响应体（官方 §4 L0-5）。
 *
 * <p>官方示例只展示 {@code {"id":1,"status":"off"}} 两个字段。
 * 这里**严格照示例**只返回这两个字段，不返回完整商品对象——
 * 题目对 L0-5 的 data 示例是明确给出的，与 L0-2 的 {@code ...} 省略写法不同，
 * 说明它就是要这两个字段。
 */
public record ProductStatusResponse(Long id, String status) {

    public static ProductStatusResponse from(Product p) {
        return new ProductStatusResponse(p.id(), p.status());
    }
}
