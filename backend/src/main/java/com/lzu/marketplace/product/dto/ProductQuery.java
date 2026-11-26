package com.lzu.marketplace.product.dto;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.common.ErrorCode;

/**
 * 商品列表的查询条件（官方 §4 L0-3）。
 *
 * <p>三个筛选条件可叠加，均为可选；省略表示不按该条件过滤。
 *
 * <p>校验放在这里而不是散落在 Controller：分页边界、状态枚举这些规则与存储层无关，
 * 集中一处便于测试和后续复用（订单列表的分页规则相同）。
 *
 * @param keyword  标题模糊匹配关键字，可为 null
 * @param status   精确匹配 on / off，可为 null（表示全部）
 * @param category 分类精确匹配，可为 null
 * @param page     页码，从 1 开始
 * @param pageSize 每页条数，1~50
 */
public record ProductQuery(
        String keyword,
        String status,
        String category,
        int page,
        int pageSize) {

    public static final int DEFAULT_PAGE = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;

    /**
     * 由原始请求参数构造并校验。
     *
     * <p>「省略」与「传了非法值」要分开处理（见 docs/P01-需求分析.md §7 #5）：
     * <ul>
     *   <li>不传 / 传空串 → 用默认值（page=1、pageSize=10），不报错；</li>
     *   <li>传了但越界 → 400 + 40001，<b>不做夹断</b>。</li>
     * </ul>
     * 前端 mock 对 pageSize 是 {@code Math.min(50, …)} 夹断，但官方 §3 码表明确
     * 「超范围 → 40001」，后端必须报错。
     */
    public static ProductQuery of(String keyword, String status, String category,
                                  Integer page, Integer pageSize) {
        int p = page == null ? DEFAULT_PAGE : page;
        int ps = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;

        if (p < 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数 page 必须是大于等于 1 的整数");
        }
        if (ps < 1 || ps > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "参数 pageSize 必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }

        String normalizedStatus = blankToNull(status);
        if (normalizedStatus != null
                && !normalizedStatus.equals("on")
                && !normalizedStatus.equals("off")) {
            // 严格匹配：题目把取值枚举为 on / off，ON / On 视为枚举非法。
            // 这是有意的选择——状态是封闭枚举，宽容大小写会让「非法值」的边界变模糊。
            throw new BusinessException(ErrorCode.BAD_REQUEST, "参数 status 只能是 on 或 off");
        }

        // keyword / category 不设长度上限：
        // 超长关键字本就应当「查不到」（返回 0 条），而不是 400——它不是格式错误。
        // 输入侧的总长度由容器/网关限制，不属于业务校验。
        return new ProductQuery(blankToNull(keyword), normalizedStatus, blankToNull(category), p, ps);
    }

    /** 空串与纯空白视为「未传」，避免 {@code ?keyword=} 被当成匹配空字符串。 */
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
