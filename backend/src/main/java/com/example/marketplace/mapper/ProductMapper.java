package com.example.marketplace.mapper;

import com.example.marketplace.domain.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface ProductMapper {

    int insert(Product product);

    Product findById(long id);

    int updateStatus(@Param("id") long id, @Param("status") String status);

    int decreaseStockIfAvailable(@Param("id") long id, @Param("quantity") int quantity);

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
