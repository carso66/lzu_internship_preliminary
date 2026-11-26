package com.example.demo.product;

import java.util.Map;

import com.example.demo.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * PATCH /api/products/{id}/status 的入参：只接受 on / off。
 */
public record ProductStatusRequest(String status) {

	public static ProductStatusRequest parse(JsonNode body) {
		if (body == null || !body.isObject()) {
			throw ApiException.badRequest("请求体必须是 JSON 对象");
		}
		JsonNode node = body.get("status");
		if (node == null || !node.isTextual()) {
			throw ApiException.badRequest("status 只能是 on 或 off");
		}
		String status = node.asText().trim();
		if (!"on".equals(status) && !"off".equals(status)) {
			throw ApiException.badRequest("status 只能是 on 或 off");
		}
		return new ProductStatusRequest(status);
	}

	/** 响应里的 data：只回显 id 和新的 status。 */
	public Map<String, Object> toResult(long id) {
		return Map.of("id", id, "status", status);
	}
}
