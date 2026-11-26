package com.lzu.xy.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCodeEnum {

    SUCCESS("0", 200, "ok"),
    INVALID_PARAMETER("40001", 400, "参数不对"),
    NOT_FOUND("40401", 404, "商品/订单/支付单不存在"),
    INSUFFICIENT_STOCK("40901", 409, "库存不足"),
    PRODUCT_OFF_SHELF("40902", 409, "商品已下架"),
    ORDER_ALREADY_PAID("40903", 409, "订单已支付，不可重复支付"),
    ORDER_NOT_PAYABLE("40904", 409, "订单当前状态不可支付"),
    INTERNAL_SERVER_ERROR("50000", 500, "服务出错");

    private  String code;
    private  int httpStatus;
    private  String message;

    public static String getByCode(String code) {
        for (ResultCodeEnum operationType : ResultCodeEnum.values()) {
            if (operationType.getCode().equals(code)) {
                return operationType.getMessage();
            }
        }
        return "";
    }
}
