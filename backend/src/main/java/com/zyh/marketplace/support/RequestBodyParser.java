package com.zyh.marketplace.support;

import com.zyh.marketplace.dto.CreateOrderRequest;
import com.zyh.marketplace.dto.CreateProductRequest;
import com.zyh.marketplace.dto.UpdateProductStatusRequest;
import com.zyh.marketplace.exception.BusinessException;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Set;

/**
 * 请求体解析器：先按 JsonNode 逐字段读取，再交业务层校验。
 *
 * <p>为什么不直接用 @RequestBody 绑定到 POJO：
 * 把 price 直接绑定成 Long 时，`19.9` 会被 Jackson 截断成 19（静默丢失精度），
 * `"1990"` 又只会抛一个笼统的解析错误、指不到字段。这里对每个字段保留原始 JSON 文本，
 * 由业务层判断「是不是整数」，违法时能明确说「字段 price 必须是整数（单位：分）」。
 *
 * <p>类型规则：字符串字段只接受 JSON 字符串；整数字段只接受 JSON 整数（不接受小数、字符串、布尔、null）。
 */
@Component
public class RequestBodyParser {

    private final ObjectMapper objectMapper;

    public RequestBodyParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public CreateProductRequest parseCreateProduct(String body) {
        JsonNode root = readObject(body);
        CreateProductRequest request = new CreateProductRequest();
        request.setTitle(stringField(root, "title"));
        request.setPrice(integerField(root, "price"));
        request.setStock(integerField(root, "stock"));
        request.setCategory(stringField(root, "category"));
        request.setDescription(stringField(root, "description"));
        request.setSellerName(stringField(root, "sellerName"));
        request.setUnknownField(firstUnknownField(root,
                Set.of("title", "price", "stock", "category", "description", "sellerName")));
        return request;
    }

    public UpdateProductStatusRequest parseUpdateStatus(String body) {
        JsonNode root = readObject(body);
        UpdateProductStatusRequest request = new UpdateProductStatusRequest();
        request.setStatus(stringField(root, "status"));
        request.setUnknownField(firstUnknownField(root, Set.of("status")));
        return request;
    }

    public CreateOrderRequest parseCreateOrder(String body) {
        JsonNode root = readObject(body);
        CreateOrderRequest request = new CreateOrderRequest();
        request.setProductId(integerField(root, "productId"));
        request.setQuantity(integerField(root, "quantity"));
        request.setBuyerName(stringField(root, "buyerName"));
        request.setBuyerPhone(stringField(root, "buyerPhone"));
        request.setAddress(stringField(root, "address"));
        request.setUnknownField(firstUnknownField(root,
                Set.of("productId", "quantity", "buyerName", "buyerPhone", "address")));
        return request;
    }

    private JsonNode readObject(String body) {
        if (body == null || body.isBlank()) {
            throw BusinessException.badRequest("请求体不能为空，需为 JSON 对象");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JacksonException e) {
            throw BusinessException.badRequest("请求体不是合法的 JSON");
        }
        if (root == null || !root.isObject()) {
            throw BusinessException.badRequest("请求体必须是 JSON 对象");
        }
        return root;
    }

    /** 字符串字段：缺省 → null；非字符串 → 40001 指明字段 */
    private String stringField(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isTextual()) {
            throw BusinessException.badRequest("字段 " + field + " 必须是字符串");
        }
        return node.asText();
    }

    /**
     * 整数字段：只接受 JSON 整数（可带正负号），返回其字面量文本，交由业务层做范围校验。
     * 小数、科学计数、字符串、布尔一律 40001。
     */
    private String integerField(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isIntegralNumber()) {
            throw BusinessException.badRequest("字段 " + field + " 必须是整数（金额单位为分，不能是小数、字符串或布尔）");
        }
        return node.asText();
    }

    private String firstUnknownField(JsonNode root, Set<String> allowed) {
        for (String name : root.propertyNames()) {
            if (!allowed.contains(name)) {
                return name;
            }
        }
        return null;
    }
}
