package com.zyh.marketplace.dto;

/**
 * L1-8 订单列表查询条件（原始字符串，Service 层统一校验）。
 *
 * @param buyerPhone 精确匹配（必填）
 * @param page       页码，默认 1
 * @param pageSize   每页条数，默认 10，范围 1~50（与商品列表约定一致）
 */
public record OrderQuery(String buyerPhone, String page, String pageSize) {

    public static final String DEFAULT_PAGE = "1";
    public static final String DEFAULT_PAGE_SIZE = "10";

    public static OrderQuery of(String buyerPhone, String page, String pageSize) {
        return new OrderQuery(buyerPhone,
                isBlank(page) ? DEFAULT_PAGE : page,
                isBlank(pageSize) ? DEFAULT_PAGE_SIZE : pageSize);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
