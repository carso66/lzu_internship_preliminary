package com.lzu.marketplace.order.dto;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.common.ErrorCode;

/**
 * 订单列表的查询条件（官方 §5 L1-8）。
 *
 * <p><b>buyerPhone 必填</b>：题目 L1-8 是「查某个手机号的订单列表」，
 * 省略手机号就意味着查全部买家的订单——既不符合接口语义，也有泄露他人订单的风险。
 * 因此缺失或空白一律 400 + 40001，而不是退化成「不过滤」。
 *
 * <p><b>分页规则复用商品模块</b>（{@code ProductQuery}）已定的约定：
 * page 默认 1、pageSize 默认 10、pageSize 范围 1~50、越界报 400 而不夹断、
 * 空串视为未传。题目未规定订单列表的分页结构，沿用同一套参数语义可以避免
 * 前端在商品页和订单页维护两套翻页逻辑。重复实现而不抽公共类，
 * 是因为两处的默认值将来可能各自调整，过早抽象反而要加参数区分。
 *
 * @param buyerPhone 买家手机号，必填（精确匹配）
 * @param page       页码，从 1 开始
 * @param pageSize   每页条数，1~50
 */
public record OrderQuery(
        String buyerPhone,
        int page,
        int pageSize) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;

    public static OrderQuery of(String buyerPhone, Integer page, Integer pageSize) {
        String phone = blankToNull(buyerPhone);
        if (phone == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数 buyerPhone 不能为空");
        }

        int p = page == null ? DEFAULT_PAGE : page;
        int ps = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;

        if (p < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数 page 必须是大于等于 1 的整数");
        }
        if (ps < 1 || ps > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "参数 pageSize 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }

        return new OrderQuery(phone, p, ps);
    }

    /** 空串与纯空白视为未传。 */
    private static String blankToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /** 偏移量。用 long 计算，避免 page 极大时 int 溢出成负数。 */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
