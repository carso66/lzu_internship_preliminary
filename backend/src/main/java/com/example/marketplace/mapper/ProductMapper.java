package com.example.marketplace.mapper;

import com.example.marketplace.domain.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductMapper {

    int insert(Product product);

    Product findById(long id);

    long count(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("category") String category
    );

    List<Product> findPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("category") String category,
            @Param("offset") long offset,
            @Param("limit") int limit
    );
}
