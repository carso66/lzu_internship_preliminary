package com.example.demo.dto;

public record CreateProductRequest( String title,
                                    Long price,
                                    Integer stock,
                                    String category,
                                    String description,
                                    String sellerName,
                                    String imageUrl) {
}
