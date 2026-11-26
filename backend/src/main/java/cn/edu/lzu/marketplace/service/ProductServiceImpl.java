package cn.edu.lzu.marketplace.service;

import cn.edu.lzu.marketplace.common.BusinessException;
import cn.edu.lzu.marketplace.common.ErrorCode;
import cn.edu.lzu.marketplace.dto.ProductCreateRequest;
import cn.edu.lzu.marketplace.dto.ProductPageResponse;
import cn.edu.lzu.marketplace.dto.ProductResponse;
import cn.edu.lzu.marketplace.dto.ProductStatusRequest;
import cn.edu.lzu.marketplace.dto.ProductStatusResponse;
import cn.edu.lzu.marketplace.model.PageResult;
import cn.edu.lzu.marketplace.model.Product;
import cn.edu.lzu.marketplace.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实现 ProductService 定义的商品业务。将控制层请求转换为领域对象，调用商品 Repository，并将查询或更新结果转换为响应 DTO。
 */
@Service
public class ProductServiceImpl implements ProductService {

    // 通过构造器依赖数据访问接口，业务逻辑可与 JDBC 实现分开验证。
    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** 发布商品：组装商品、写入默认状态和时间，保存后使用生成的主键构造响应；组装商品对象，设置默认的状态和时间，保存商品 */
    @Override
    public ProductResponse create(ProductCreateRequest request) {
        // 发布请求只携带商品内容；初始上架状态与创建时间由服务端补齐。
        Product product = new Product(
                null,
                request.title(),
                request.price(),
                request.stock(),
                request.category(),
                request.description(),
                Product.STATUS_ON,
                request.sellerName(),
                LocalDateTime.now());
        Long id = productRepository.insert(product);
        return ProductResponse.from(product.withId(id));
    }

    /** 商品详情：将空查询结果转换为资源不存在异常。 */
    @Override
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"));
        return ProductResponse.from(product);
    }

    /** 筛选与分页：把对外页码转换为数据库偏移量，并补齐响应分页信息；计算分页偏移量，调用数据访问层查询 */
    @Override
    public ProductPageResponse list(Integer page, Integer pageSize, String keyword, String status, String category) {
        int p = page == null ? 1 : page;
        int size = pageSize == null ? 10 : pageSize;
        // 页码从 1 开始、数据库偏移从 0 开始；乘法使用 long，避免大页码时的 int 溢出。
        long offset = (long) (p - 1) * size;

        // 数据访问层返回匹配总数和领域对象，响应仅转换当前页的商品。
        PageResult<Product> result = productRepository.findPage(offset, size, keyword, status, category);
        List<ProductResponse> items = result.items().stream().map(ProductResponse::from).toList();
        return new ProductPageResponse(result.total(), p, size, items);
    }

    /** 上下架：将商品更新为请求指定的状态，UPDATE 影响 0 行时视为商品不存在。 */
    @Override
    public ProductStatusResponse updateStatus(Long id, ProductStatusRequest request) {
        int updated = productRepository.updateStatus(id, request.status());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在");
        }
        return new ProductStatusResponse(id, request.status());
    }
}
