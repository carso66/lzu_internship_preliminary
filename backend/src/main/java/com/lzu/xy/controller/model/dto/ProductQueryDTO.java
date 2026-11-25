package com.lzu.xy.controller.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 商品列表查询条件。三个筛选条件可叠加。
 */
@Data
public class ProductQueryDTO {

    /** 标题模糊匹配 */
    private String keyword;

    /** on / off，不传（或传空串）表示全部 */
    @Pattern(regexp = "^$|on|off", message = "只能是 on 或 off")
    private String status;

    /** 分类精确匹配 */
    private String category;

    @Min(value = 1, message = "不能小于1")
    private Integer page = 1;

    @Min(value = 1, message = "不能小于1")
    @Max(value = 50, message = "不能大于50")
    private Integer pageSize = 10;
}
