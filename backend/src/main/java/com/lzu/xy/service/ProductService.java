package com.lzu.xy.service;

import com.lzu.xy.domain.Product;
import com.lzu.xy.controller.model.dto.ProductCreateDTO;
import com.lzu.xy.controller.model.dto.ProductQueryDTO;
import com.lzu.xy.controller.model.dto.ProductStatusDTO;
import com.lzu.xy.enums.ProductStatusEnum;
import com.lzu.xy.enums.ResultCodeEnum;
import com.lzu.xy.exception.BusinessException;
import com.lzu.xy.repository.ProductRepository;
import com.lzu.xy.util.SnowflakeIdGenerator;
import com.lzu.xy.controller.model.vo.PageVO;
import com.lzu.xy.controller.model.vo.ProductStatusVO;
import com.lzu.xy.controller.model.vo.ProductVO;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final SnowflakeIdGenerator idGenerator;

    @Transactional
    public ProductVO create(ProductCreateDTO dto) {
        LocalDateTime now = LocalDateTime.now();
        Product product = Product.builder()
                .id(idGenerator.nextId())
                .title(dto.getTitle())
                .price(dto.getPrice())
                .stock(dto.getStock())
                .category(dto.getCategory())
                .description(dto.getDescription())
                .status(ProductStatusEnum.ON.getValue())
                .sellerName(dto.getSellerName())
                .createdAt(now)
                .updatedAt(now)
                .isDeleted(0)
                .build();
        return ProductVO.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public PageVO<ProductVO> list(ProductQueryDTO query) {
        // page 从 1 开始，Spring Data 从 0 开始
        Pageable pageable = PageRequest.of(query.getPage() - 1, query.getPageSize(),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Product> result = productRepository.findAll(buildSpecification(query), pageable);
        List<ProductVO> items = result.getContent().stream()
                .map(ProductVO::from)
                .toList();
        return PageVO.<ProductVO>builder()
                .total(result.getTotalElements())
                .page(query.getPage())
                .pageSize(query.getPageSize())
                .items(items)
                .build();
    }

    @Transactional(readOnly = true)
    public ProductVO getById(Long id) {
        return ProductVO.from(requireProduct(id));
    }

    @Transactional
    public ProductStatusVO updateStatus(Long id, ProductStatusDTO dto) {
        ProductStatusEnum status = ProductStatusEnum.fromLabel(dto.getStatus())
                .orElseThrow(() -> new BusinessException(ResultCodeEnum.INVALID_PARAMETER, "status 只能是 on 或 off"));
        Product product = requireProduct(id);
        product.setStatus(status.getValue());
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);
        return ProductStatusVO.builder()
                .id(product.getId())
                .status(status.getLabel())
                .build();
    }

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
                .filter(product -> product.getIsDeleted() == null || product.getIsDeleted() == 0)
                .orElseThrow(() -> new BusinessException(ResultCodeEnum.NOT_FOUND));
    }

    /** keyword 模糊匹配标题，status/category 精确匹配，三者可叠加 */
    private Specification<Product> buildSpecification(ProductQueryDTO query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("isDeleted"), 0));
            if (StringUtils.hasText(query.getKeyword())) {
                predicates.add(cb.like(root.get("title"), "%" + escapeLike(query.getKeyword()) + "%", '\\'));
            }
            if (StringUtils.hasText(query.getStatus())) {
                ProductStatusEnum.fromLabel(query.getStatus())
                        .ifPresent(status -> predicates.add(cb.equal(root.get("status"), status.getValue())));
            }
            if (StringUtils.hasText(query.getCategory())) {
                predicates.add(cb.equal(root.get("category"), query.getCategory()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private String escapeLike(String keyword) {
        return keyword.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
