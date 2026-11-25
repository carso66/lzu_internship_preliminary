package com.zyh.marketplace.dto;

/**
 * POST /api/orders 的请求体。
 *
 * <p>与 CreateProductRequest 同一套思路：不使用 POJO 自动绑定，字段以原始文本保留，
 * 由支撑层保证 JSON 类型（整数 / 字符串），业务层做「必填、范围、格式」校验，
 * `"2"`、1.5、true 这类非法类型能在报错里明确指出字段名。
 * price / unitPrice / totalAmount 不在请求字段中——价格一律由服务端计算。
 */
public class CreateOrderRequest {

    private String productId;
    private String quantity;
    private String buyerName;
    private String buyerPhone;
    private String address;
    /** 未知字段名（用于提示拼错），null 表示没有 */
    private String unknownField;

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getQuantity() {
        return quantity;
    }

    public void setQuantity(String quantity) {
        this.quantity = quantity;
    }

    public String getBuyerName() {
        return buyerName;
    }

    public void setBuyerName(String buyerName) {
        this.buyerName = buyerName;
    }

    public String getBuyerPhone() {
        return buyerPhone;
    }

    public void setBuyerPhone(String buyerPhone) {
        this.buyerPhone = buyerPhone;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getUnknownField() {
        return unknownField;
    }

    public void setUnknownField(String unknownField) {
        this.unknownField = unknownField;
    }
}
