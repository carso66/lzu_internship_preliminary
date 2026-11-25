package com.lzu.xy.repository;

import com.lzu.xy.domain.Product;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

public interface ProductRepository
        extends CrudRepository<Product, Long>,
        PagingAndSortingRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {

    /**
     * 原子扣减库存：只有库存足够时才会更新成功。
     * 返回受影响行数，0 表示库存不足（并发下也不会把库存扣成负数）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Product p
               set p.stock = p.stock - :quantity,
                   p.updatedAt = :updatedAt
             where p.id = :id
               and p.isDeleted = 0
               and p.stock >= :quantity
            """)
    int deductStock(@Param("id") Long id,
                    @Param("quantity") Integer quantity,
                    @Param("updatedAt") LocalDateTime updatedAt);
}
