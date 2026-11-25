package com.lzu.xy.controller.model.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 修改上下架状态后的返回结构，仅含 id 和最新状态。
 */
@Data
@Builder
public class ProductStatusVO {

    private Long id;

    /** on / off */
    private String status;
}
