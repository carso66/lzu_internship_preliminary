package com.lzu.xy.repository;

import com.lzu.xy.domain.Product;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;

public interface ProductRepository
        extends CrudRepository<Product, Long>,
        PagingAndSortingRepository<Product, Long>,
        JpaSpecificationExecutor<Product> {
}
