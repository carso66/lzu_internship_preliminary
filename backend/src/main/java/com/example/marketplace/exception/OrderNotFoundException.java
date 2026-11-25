package com.example.marketplace.exception;

public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String orderNo) {
        super("order " + orderNo + " not found");
    }
}
