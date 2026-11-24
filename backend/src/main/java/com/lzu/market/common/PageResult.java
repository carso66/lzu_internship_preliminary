package com.lzu.market.common;

import java.util.List;

/**
 * 分页结果，放在 data 里：{"total":0,"page":1,"pageSize":10,"items":[]}
 */
public record PageResult<T>(long total, int page, int pageSize, List<T> items) {
}
