package cn.edu.lzu.marketplace.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/products 请求体。
 *
 * <p>price 为 Long，以「分」为单位的整数（1990 表示 ¥19.90）；
 * 配合 JacksonConfig 的严格整数反序列化，字符串形式的数字或小数会直接 400。
 */
public record ProductCreateRequest(
        @NotBlank(message = "标题不能为空")
        @Size(max = 100, message = "标题长度不能超过 100")
        String title,

        @NotNull(message = "价格不能为空")
        @Min(value = 0, message = "价格不能为负数")
        Long price,

        @NotNull(message = "库存不能为空")
        @Min(value = 0, message = "库存不能为负数")
        Integer stock,

        @NotBlank(message = "分类不能为空")
        @Size(max = 50, message = "分类长度不能超过 50")
        String category,

        @Size(max = 500, message = "备注长度不能超过 500")
        String description,

        @NotBlank(message = "卖家名称不能为空")
        @Size(max = 50, message = "卖家名称长度不能超过 50")
        String sellerName) {
}
