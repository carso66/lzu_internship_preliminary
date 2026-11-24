package cn.edu.lzu.marketplace.service;

import cn.edu.lzu.marketplace.dto.ProductCreateRequest;
import cn.edu.lzu.marketplace.dto.ProductPageResponse;
import cn.edu.lzu.marketplace.dto.ProductResponse;
import cn.edu.lzu.marketplace.dto.ProductStatusRequest;
import cn.edu.lzu.marketplace.dto.ProductStatusResponse;

/**
 * 商品业务接口。
 */
public interface ProductService {

    ProductResponse create(ProductCreateRequest request);

    ProductResponse getById(Long id);

    ProductPageResponse list(Integer page, Integer pageSize, String keyword, String status, String category);

    ProductStatusResponse updateStatus(Long id, ProductStatusRequest request);
}
