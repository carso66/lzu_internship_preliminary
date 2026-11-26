package com.lzu.xy.controller.model.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 分页返回结构。
 */
@Data
@Builder
public class PageVO<T> {

    /** 筛选后的总条数 */
    private long total;

    private int page;

    private int pageSize;

    private List<T> items;
}
