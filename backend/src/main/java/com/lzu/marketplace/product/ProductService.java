package com.lzu.marketplace.product;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.common.ErrorCode;
import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.product.dto.CreateProductRequest;
import com.lzu.marketplace.product.dto.ProductQuery;
import com.lzu.marketplace.product.dto.ProductResponse;
import com.lzu.marketplace.product.dto.ProductStatusResponse;
import com.lzu.marketplace.product.dto.UpdateProductStatusRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 商品业务逻辑。
 *
 * <p>已实现：发布（L0-2）、列表（L0-3）。详情 / 上下架在后续阶段补充。
 */
@Service
public class ProductService {

    private final ProductRepository repository;

    public ProductService(ProductRepository repository) {
        this.repository = repository;
    }

    /**
     * 发布商品。新商品固定为「上架」状态（官方 §4 L0-2 示例返回 {@code "status":"on"}）。
     *
     * <p>事务说明：单条 INSERT 本身原子，这里仍显式标注，是为了后续列表/详情等
     * 多写操作加入时事务边界不被误改。
     */
    @Transactional
    public ProductResponse create(CreateProductRequest req) {
        CreateProductRequest normalized = normalize(req);
        Product saved = repository.insert(normalized, Product.STATUS_ON);
        return ProductResponse.from(saved);
    }

    /**
     * 分页查询商品列表（官方 §4 L0-3）。
     *
     * <p>筛选条件的校验在 {@link ProductQuery#of} 里完成，这里只负责查询与 DTO 转换。
     * 空结果返回 {@code items: []}，不是 null。
     */
    public PageResult<ProductResponse> list(ProductQuery query) {
        PageResult<Product> page = repository.search(query);
        List<ProductResponse> items = page.items().stream()
                .map(ProductResponse::from)
                .toList();
        return PageResult.of(page.total(), page.page(), page.pageSize(), items);
    }

    /**
     * 按 id 查询商品详情（官方 §4 L0-4）。不存在抛 404 + 40401。
     */
    public ProductResponse getById(long id) {
        return repository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在"));
    }

    /**
     * 更新商品上下架状态（官方 §4 L0-5）。
     *
     * <p>不存在 → 404 + 40401。状态值合法性已由 {@link UpdateProductStatusRequest} 的
     * {@code @Pattern} 拦截，走到这里时一定是 on 或 off。
     *
     * <p>重复设置相同状态是幂等的：结果仍是 200，返回当前状态，不报错。
     * 这与「订单已支付不可重复支付」不同——上下架是幂等的赋值操作，不是状态机迁移。
     */
    @Transactional
    public ProductStatusResponse updateStatus(long id, String status) {
        int affected = repository.updateStatus(id, status);
        if (affected == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "商品不存在");
        }
        return repository.findById(id)
                .map(ProductStatusResponse::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "商品不存在"));
    }

    /**
     * 去除首尾空白。
     *
     * <p>为什么在这做而不是让客户端负责：
     * <ul>
     *   <li>{@code "   "} 能通过 {@code @NotBlank} 的长度检查吗？不能——@NotBlank 已拦截纯空白，
     *       但 {@code "  商品  "} 会原样入库，导致列表关键字匹配与展示出现前后空格；</li>
     *   <li>描述为空串时统一存 null，避免「有值但无内容」和「没传」两种语义混在库里。</li>
     * </ul>
     * 归一化在后置校验之后执行，所以不会把非法输入"洗白"成合法。
     */
    private CreateProductRequest normalize(CreateProductRequest req) {
        String description = StringUtils.hasText(req.description()) ? req.description().trim() : null;
        return new CreateProductRequest(
                req.title().trim(),
                req.price(),
                req.stock(),
                req.category().trim(),
                description,
                req.sellerName().trim());
    }
}
