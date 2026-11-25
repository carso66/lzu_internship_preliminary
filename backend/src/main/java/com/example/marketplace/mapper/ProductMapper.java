package com.example.marketplace.mapper;

import com.example.marketplace.domain.Product;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 商品数据访问接口。
 */
public interface ProductMapper {

    int insert(Product product);

    Product findById(long id);

    int updateStatus(@Param("id") long id, @Param("status") String status);

    /**
     * 原子扣减库存，仅当商品已上架且库存足够时更新成功。
     *
     * @return 影响行数；0 表示商品不存在、已下架或库存不足
     */
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
