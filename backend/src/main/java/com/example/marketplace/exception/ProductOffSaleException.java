package com.example.marketplace.exception;

public class ProductOffSaleException extends RuntimeException {

    public ProductOffSaleException(long productId) {
        super("product " + productId + " is off sale");
    }
}
