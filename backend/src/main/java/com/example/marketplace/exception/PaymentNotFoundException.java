package com.example.marketplace.exception;

public class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(String paymentId) {
        super("payment " + paymentId + " not found");
    }
}
