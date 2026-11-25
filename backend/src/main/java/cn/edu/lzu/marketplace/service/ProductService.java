package cn.edu.lzu.marketplace.service;

import cn.edu.lzu.marketplace.dto.ProductCreateRequest;
import cn.edu.lzu.marketplace.dto.ProductPageResponse;
import cn.edu.lzu.marketplace.dto.ProductResponse;
import cn.edu.lzu.marketplace.dto.ProductStatusRequest;
import cn.edu.lzu.marketplace.dto.ProductStatusResponse;

/**
 * 供商品控制器调用的业务接口，使用 DTO 表达请求和响应；规定了要做什么。
 */
public interface ProductService {

    /** 发布商品，由业务层设置初始状态和创建时间。 */
    ProductResponse create(ProductCreateRequest request);

    /** 按商品 ID 查询详情，不存在时抛出资源不存在异常。 */
    ProductResponse getById(Long id);

    /** 按筛选条件返回商品列表及分页信息。 */
    ProductPageResponse list(Integer page, Integer pageSize, String keyword, String status, String category);

    /** 变更上架状态，返回商品 ID 和更新后的状态。 */
    ProductStatusResponse updateStatus(Long id, ProductStatusRequest request);
}
