package com.example.demo.product;

import java.util.List;
import java.util.Set;

import com.example.demo.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * POST /api/products 的入参。
 *
 * 直接用 {@link JsonNode} 逐字段校验而不是绑到 DTO：Jackson 默认会把 "1990" 这类
 * 字符串、甚至 1990.5 这样的浮点静默转成 int，那样非法类型就漏过去了。
 * 这里显式要求「JSON 数字且是整数」，错误 message 也能指明具体字段。
 */
public record ProductCreateRequest(
		String title,
		int price,
		int stock,
		String category,
		String description,
		String sellerName) {

	/** 前端下拉的推荐分类，接口不接受表外取值。 */
	public static final Set<String> CATEGORIES = Set.of("数码", "图书", "服饰", "食品", "文具");

	private static final int TITLE_MAX = 50;
	private static final int STOCK_MAX = 999;
	private static final int DESCRIPTION_MAX = 200;
	private static final int SELLER_NAME_MAX = 64;

	public static ProductCreateRequest parse(JsonNode body) {
		if (body == null || !body.isObject()) {
			throw ApiException.badRequest("请求体必须是 JSON 对象");
		}

		return new ProductCreateRequest(
				parseTitle(body.get("title")),
				parsePrice(body.get("price")),
				parseStock(body.get("stock")),
				parseCategory(body.get("category")),
				parseDescription(body.get("description")),
				parseSellerName(body.get("sellerName")));
	}

	private static String parseTitle(JsonNode node) {
		if (node == null || !node.isTextual()) {
			throw ApiException.badRequest("标题需为 1~50 个字符");
		}
		String title = node.asText().trim();
		if (title.isEmpty() || title.length() > TITLE_MAX) {
			throw ApiException.badRequest("标题需为 1~50 个字符");
		}
		return title;
	}

	private static int parsePrice(JsonNode node) {
		if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
			throw ApiException.badRequest("价格必须为正整数（单位：分）");
		}
		int price = node.intValue();
		if (price <= 0) {
			throw ApiException.badRequest("价格必须为正整数（单位：分）");
		}
		return price;
	}

	private static int parseStock(JsonNode node) {
		if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
			throw ApiException.badRequest("库存需为 0~999 的整数");
		}
		int stock = node.intValue();
		if (stock < 0 || stock > STOCK_MAX) {
			throw ApiException.badRequest("库存需为 0~999 的整数");
		}
		return stock;
	}

	private static String parseCategory(JsonNode node) {
		if (node == null || !node.isTextual() || !CATEGORIES.contains(node.asText())) {
			throw ApiException.badRequest("分类不合法（可选值：" + String.join("、", CATEGORIES.stream().sorted().toList()) + "）");
		}
		return node.asText();
	}

	private static String parseDescription(JsonNode node) {
		if (node == null || node.isNull()) {
			return "";
		}
		if (!node.isTextual()) {
			throw ApiException.badRequest("描述需为字符串，且不能超过 200 字");
		}
		String description = node.asText().trim();
		// 与 products.description VARCHAR(200) 的口径保持一致（按 Java 字符数）
		if (description.length() > DESCRIPTION_MAX) {
			throw ApiException.badRequest("描述不能超过 200 字");
		}
		return description;
	}

	private static String parseSellerName(JsonNode node) {
		if (node == null || !node.isTextual()) {
			throw ApiException.badRequest("卖家名不能为空");
		}
		String sellerName = node.asText().trim();
		if (sellerName.isEmpty()) {
			throw ApiException.badRequest("卖家名不能为空");
		}
		if (sellerName.length() > SELLER_NAME_MAX) {
			throw ApiException.badRequest("卖家名不能超过 " + SELLER_NAME_MAX + " 个字符");
		}
		return sellerName;
	}
}
