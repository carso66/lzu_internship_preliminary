package com.example.demo.order;

import com.example.demo.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * POST /api/orders 的入参。
 *
 * 与 {@code ProductCreateRequest} 一样用 {@link JsonNode} 逐字段校验：
 * Jackson 绑 DTO 时会把 "2" 这种字符串、甚至 2.5 这样的浮点静默转成 int，
 * 那样 quantity 的非法类型就漏过去了，而这里必须让它们返回 400。
 *
 * 金额不在这里：unitPrice / totalAmount 由服务端按数据库里的商品价格算，
 * 客户端就算多传 unitPrice 也不会被读取。
 */
public record OrderCreateRequest(
		long productId,
		int quantity,
		String buyerName,
		String buyerPhone,
		String address) {

	private static final int BUYER_NAME_MAX = 50;
	private static final int BUYER_PHONE_LENGTH = 11;
	private static final int ADDRESS_MAX = 255;

	public static OrderCreateRequest parse(JsonNode body) {
		if (body == null || !body.isObject()) {
			throw ApiException.badRequest("请求体必须是 JSON 对象");
		}

		return new OrderCreateRequest(
				parseProductId(body.get("productId")),
				parseQuantity(body.get("quantity")),
				parseBuyerName(body.get("buyerName")),
				parseBuyerPhone(body.get("buyerPhone")),
				parseAddress(body.get("address")));
	}

	private static long parseProductId(JsonNode node) {
		if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()) {
			throw ApiException.badRequest("productId 必须为正整数");
		}
		long productId = node.longValue();
		if (productId <= 0) {
			throw ApiException.badRequest("productId 必须为正整数");
		}
		return productId;
	}

	/** quantity 必须是 JSON 整数且 ≥1：0、-1、"2"、2.5 全部拒绝。 */
	private static int parseQuantity(JsonNode node) {
		if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
			throw ApiException.badRequest("quantity 必须是大于等于 1 的整数");
		}
		int quantity = node.intValue();
		if (quantity < 1) {
			throw ApiException.badRequest("quantity 必须是大于等于 1 的整数");
		}
		return quantity;
	}

	private static String parseBuyerName(JsonNode node) {
		if (node == null || !node.isTextual()) {
			throw ApiException.badRequest("收货人姓名不能为空，且不能超过 " + BUYER_NAME_MAX + " 个字符");
		}
		String buyerName = node.asText().trim();
		if (buyerName.isEmpty() || buyerName.length() > BUYER_NAME_MAX) {
			throw ApiException.badRequest("收货人姓名不能为空，且不能超过 " + BUYER_NAME_MAX + " 个字符");
		}
		return buyerName;
	}

	/** 手机号只收 11 位数字，避免把带空格、带连字符的写法存进库后列表查不到。 */
	private static String parseBuyerPhone(JsonNode node) {
		if (node == null || !node.isTextual()) {
			throw ApiException.badRequest("手机号需为 " + BUYER_PHONE_LENGTH + " 位数字");
		}
		String buyerPhone = node.asText().trim();
		if (buyerPhone.length() != BUYER_PHONE_LENGTH) {
			throw ApiException.badRequest("手机号需为 " + BUYER_PHONE_LENGTH + " 位数字");
		}
		for (int i = 0; i < buyerPhone.length(); i++) {
			if (!Character.isDigit(buyerPhone.charAt(i))) {
				throw ApiException.badRequest("手机号需为 " + BUYER_PHONE_LENGTH + " 位数字");
			}
		}
		return buyerPhone;
	}

	private static String parseAddress(JsonNode node) {
		if (node == null || !node.isTextual()) {
			throw ApiException.badRequest("收货地址不能为空，且不能超过 " + ADDRESS_MAX + " 个字符");
		}
		String address = node.asText().trim();
		if (address.isEmpty() || address.length() > ADDRESS_MAX) {
			throw ApiException.badRequest("收货地址不能为空，且不能超过 " + ADDRESS_MAX + " 个字符");
		}
		return address;
	}
}
