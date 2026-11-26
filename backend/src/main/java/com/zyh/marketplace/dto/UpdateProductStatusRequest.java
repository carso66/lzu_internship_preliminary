package com.zyh.marketplace.dto;

/**
 * PATCH /api/products/{id}/status 的请求体。
 * status 以字符串原样保留，Service 层校验 on/off 并给出明确的错误字段。
 */
public class UpdateProductStatusRequest {

    private String status;
    /** 请求体里除 status 之外的未知字段（用于提示），null 表示没有 */
    private String unknownField;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getUnknownField() {
        return unknownField;
    }

    public void setUnknownField(String unknownField) {
        this.unknownField = unknownField;
    }
}
