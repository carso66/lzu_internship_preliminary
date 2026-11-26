package com.example.demo.product;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.common.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	private final ProductService productService;

	public ProductController(ProductService productService) {
		this.productService = productService;
	}

	/** L0-2 发布商品：成功返回 201，data 为落库后的商品。 */
	@PostMapping
	public ResponseEntity<ApiResponse<Product>> create(@RequestBody(required = false) JsonNode body) {
		Product product = productService.create(ProductCreateRequest.parse(body));
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(product));
	}

	/** L0-3 商品列表：keyword / status / category 可叠加，page 与 pageSize 有默认值。 */
	@GetMapping
	public ApiResponse<ProductPage> list(
			@RequestParam(required = false) String keyword,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer pageSize) {
		ProductListQuery query = ProductListQuery.parse(keyword, status, category, page, pageSize);
		return ApiResponse.ok(productService.list(query));
	}

	/** L0-4 商品详情：不存在 → 404 + 40401。 */
	@GetMapping("/{id}")
	public ApiResponse<Product> detail(@PathVariable long id) {
		return ApiResponse.ok(productService.get(id));
	}

	/** L0-5 上架 / 下架：请求体 {"status":"off"}，成功返回 200 与 {id, status}。 */
	@PatchMapping("/{id}/status")
	public ApiResponse<Map<String, Object>> updateStatus(
			@PathVariable long id,
			@RequestBody(required = false) JsonNode body) {
		return ApiResponse.ok(productService.updateStatus(id, ProductStatusRequest.parse(body)));
	}
}
