package com.campus.marketplace.dto;

/**
 * 发布商品请求体。
 * price / stock 故意用 String 接收，便于手写"纯整数"精确校验；
 * id / status / createdAt 由服务端或数据库决定，不接收。
 */
public class CreateProductRequest {

    private String title;
    private String price;
    private String stock;
    private String category;
    private String description;
    private String sellerName;

    public CreateProductRequest() {
    }

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
}
