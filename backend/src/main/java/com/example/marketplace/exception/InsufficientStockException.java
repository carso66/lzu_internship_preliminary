package com.example.marketplace.exception;

public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(long productId) {
        super("product " + productId + " has insufficient stock");
    }
}
