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
 * 商品业务实现：负责领域组装、默认状态、不存在/非法业务规则的判定与翻译。
 */
@Service
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public ProductResponse create(ProductCreateRequest request) {
        // status 默认 on；created_at 由服务端写入
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

    @Override
    public ProductResponse getById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"));
        return ProductResponse.from(product);
    }

    @Override
    public ProductPageResponse list(Integer page, Integer pageSize, String keyword, String status, String category) {
        int p = page == null ? 1 : page;
        int size = pageSize == null ? 10 : pageSize;
        // 用 long 计算偏移量：(p-1)*size 在 page 接近 Integer.MAX_VALUE 时会 int 溢出变负
        long offset = (long) (p - 1) * size;

        PageResult<Product> result = productRepository.findPage(offset, size, keyword, status, category);
        List<ProductResponse> items = result.items().stream().map(ProductResponse::from).toList();
        return new ProductPageResponse(result.total(), p, size, items);
    }

    @Override
    public ProductStatusResponse updateStatus(Long id, ProductStatusRequest request) {
        int updated = productRepository.updateStatus(id, request.status());
        if (updated == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在");
        }
        return new ProductStatusResponse(id, request.status());
    }
}
