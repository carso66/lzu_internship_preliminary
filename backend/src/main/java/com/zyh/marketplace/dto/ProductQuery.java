package com.zyh.marketplace.dto;

/**
 * 商品列表的查询条件（字符串形式保留原始入参，便于在 Service 层统一校验报错）。
 *
 * @param keyword  标题模糊匹配，可选
 * @param status   on / off，可选
 * @param category 分类精确匹配，可选
 * @param page     页码，默认 1
 * @param pageSize 每页条数，默认 10，范围 1~50
 */
public record ProductQuery(String keyword, String status, String category, String page, String pageSize) {

    public static final String DEFAULT_PAGE = "1";
    public static final String DEFAULT_PAGE_SIZE = "10";

    public static ProductQuery of(String keyword, String status, String category, String page, String pageSize) {
        return new ProductQuery(keyword, status, category,
                isBlank(page) ? DEFAULT_PAGE : page,
                isBlank(pageSize) ? DEFAULT_PAGE_SIZE : pageSize);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
