package com.example.marketplace.exception;

public class OrderAlreadyPaidException extends RuntimeException {

    public OrderAlreadyPaidException(String orderNo) {
        super("订单已支付，请勿重复支付");
    }
}
