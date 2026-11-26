package com.zyh.marketplace.dto;

/**
 * POST /api/products 的请求体。
 *
 * <p>刻意**不使用** Bean Validation 注解、也不使用 record/Lombok 自动绑定：
 * 字段类型交给我们自己的解析器处理，才能对「price 传成 19.9 或 "1990"」这类非法类型
 * 给出明确到字段的错误（40001），而不是笼统的 JSON 解析失败。
 *
 * <p>字段名与 JSON 契约一致（camelCase）。
 */
public class CreateProductRequest {

    private String title;
    private String price;
    private String stock;
    private String category;
    private String description;
    private String sellerName;

    /** 是否为未知字段，用于提示客户端传错了字段名；null 表示无未知字段 */
    private String unknownField;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getStock() {
        return stock;
    }

    public void setStock(String stock) {
        this.stock = stock;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSellerName() {
        return sellerName;
    }

    public void setSellerName(String sellerName) {
        this.sellerName = sellerName;
    }

    public String getUnknownField() {
        return unknownField;
    }

    public void setUnknownField(String unknownField) {
        this.unknownField = unknownField;
    }
}
