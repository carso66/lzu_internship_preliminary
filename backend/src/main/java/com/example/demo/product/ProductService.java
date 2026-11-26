package com.example.demo.product;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.common.ApiException;

@Service
public class ProductService {

	private final ProductRepository productRepository;

	public ProductService(ProductRepository productRepository) {
		this.productRepository = productRepository;
	}

	@Transactional
	public Product create(ProductCreateRequest request) {
		return productRepository.insert(request);
	}

	/** L0-3 列表：total 是筛选后的总条数，不是全表条数。 */
	@Transactional(readOnly = true)
	public ProductPage list(ProductListQuery query) {
		long total = productRepository.count(query);
		// 总数为 0 时没必要再查一次明细
		if (total == 0) {
			return new ProductPage(0, query.page(), query.pageSize(), List.of());
		}
		return new ProductPage(total, query.page(), query.pageSize(), productRepository.findPage(query));
	}

	/** L0-4 详情：不存在即 404 + 40401。 */
	@Transactional(readOnly = true)
	public Product get(long id) {
		return productRepository.findById(id)
			.orElseThrow(() -> ApiException.notFound("商品不存在"));
	}

	/** L0-5 上下架：先确认存在，再更新，避免把 404 变成影响行数 0 的静默成功。 */
	@Transactional
	public Map<String, Object> updateStatus(long id, ProductStatusRequest request) {
		get(id);
		if (productRepository.updateStatus(id, request.status()) == 0) {
			throw ApiException.notFound("商品不存在");
		}
		return request.toResult(id);
	}
}
