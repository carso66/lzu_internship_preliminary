package com.example.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 覆盖 L1-7 订单详情与 L1-8 订单列表。
 *
 * 列表按手机号过滤，所以每条用例用独立手机号圈定自己的订单，
 * 不依赖全表条数。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderQueryTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void detailReturnsOrderByOrderNo() throws Exception {
		long productId = createProduct(uniqueToken() + " 详情", 2500, 10);
		String phone = uniquePhone();
		String orderNo = placeOrder(productId, 2, phone);

		mockMvc.perform(get("/api/orders/{orderNo}", orderNo))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.orderNo").value(orderNo))
			.andExpect(jsonPath("$.data.productId").value(productId))
			.andExpect(jsonPath("$.data.quantity").value(2))
			.andExpect(jsonPath("$.data.unitPrice").value(2500))
			.andExpect(jsonPath("$.data.totalAmount").value(5000))
			.andExpect(jsonPath("$.data.buyerPhone").value(phone))
			.andExpect(jsonPath("$.data.address").value("兰州市城关区天水南路222号"));
	}

	@Test
	void detailReturns404ForUnknownOrderNo() throws Exception {
		mockMvc.perform(get("/api/orders/{orderNo}", "SO199901019999"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(40401))
			.andExpect(jsonPath("$.message").value("订单不存在"))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	void listReturnsEmptyPageForBuyerWithoutOrders() throws Exception {
		mockMvc.perform(get("/api/orders").param("buyerPhone", uniquePhone()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.total").value(0))
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.pageSize").value(10))
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void listReturnsBuyersOrdersNewestFirst() throws Exception {
		long productId = createProduct(uniqueToken() + " 列表", 100, 10);
		String phone = uniquePhone();

		String first = placeOrder(productId, 1, phone);
		String second = placeOrder(productId, 2, phone);
		String third = placeOrder(productId, 3, phone);

		String response = mockMvc.perform(get("/api/orders").param("buyerPhone", phone))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.items.length()").value(3))
			.andReturn().getResponse().getContentAsString();

		List<String> orderNos = new ArrayList<>();
		for (JsonNode item : objectMapper.readTree(response).path("data").path("items")) {
			orderNos.add(item.path("orderNo").asText());
		}
		// 创建时间倒序：最后下的那单排最前
		assertThat(orderNos).containsExactly(third, second, first);
	}

	@Test
	void listSplitsPagesAndReportsFilteredTotal() throws Exception {
		long productId = createProduct(uniqueToken() + " 分页", 100, 10);
		String phone = uniquePhone();
		placeOrder(productId, 1, phone);
		placeOrder(productId, 1, phone);
		placeOrder(productId, 1, phone);

		mockMvc.perform(get("/api/orders")
				.param("buyerPhone", phone)
				.param("page", "1")
				.param("pageSize", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.pageSize").value(2))
			.andExpect(jsonPath("$.data.items.length()").value(2));

		mockMvc.perform(get("/api/orders")
				.param("buyerPhone", phone)
				.param("page", "2")
				.param("pageSize", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.items.length()").value(1));

		// 越界页返回空数组，total 仍是筛选后的总数
		mockMvc.perform(get("/api/orders")
				.param("buyerPhone", phone)
				.param("page", "9")
				.param("pageSize", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void listDoesNotExposeOtherBuyersOrders() throws Exception {
		long productId = createProduct(uniqueToken() + " 隔离", 100, 10);
		String mine = uniquePhone();
		String others = uniquePhone();
		placeOrder(productId, 1, others);
		placeOrder(productId, 1, others);

		mockMvc.perform(get("/api/orders").param("buyerPhone", mine))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(0))
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void listRejectsMissingBuyerPhone() throws Exception {
		assertBadRequest("");

		// 只有空白的手机号按「未传」处理，不做 trim 之外的兜底
		mockMvc.perform(get("/api/orders").param("buyerPhone", "   "))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001))
			.andExpect(jsonPath("$.message").value("buyerPhone 不能为空"));
	}

	@Test
	void listRejectsInvalidPaging() throws Exception {
		String phone = uniquePhone();
		assertBadRequest("?buyerPhone=" + phone + "&page=0", "page 必须是不小于 1 的整数");
		assertBadRequest("?buyerPhone=" + phone + "&page=abc", "参数 page 类型不正确");
		assertBadRequest("?buyerPhone=" + phone + "&pageSize=0", "pageSize 需在 1~50 之间");
		assertBadRequest("?buyerPhone=" + phone + "&pageSize=51", "pageSize 需在 1~50 之间");
	}

	private void assertBadRequest(String query) throws Exception {
		assertBadRequest(query, "buyerPhone 不能为空");
	}

	private void assertBadRequest(String query, String expectedMessage) throws Exception {
		mockMvc.perform(get("/api/orders" + query))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001))
			.andExpect(jsonPath("$.message").value(expectedMessage));
	}

	private String placeOrder(long productId, int quantity, String phone) throws Exception {
		String body = """
				{"productId":%d,"quantity":%d,"buyerName":"张三","buyerPhone":"%s","address":"兰州市城关区天水南路222号"}
				""".formatted(productId, quantity, phone);

		String response = mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response).path("data").path("orderNo").asText();
	}

	private static String uniqueToken() {
		return "t" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	private static String uniquePhone() {
		long tail = Math.abs(java.util.UUID.randomUUID().getMostSignificantBits() % 100_000_000L);
		return "138" + String.format("%08d", tail);
	}

	private long createProduct(String title, int price, int stock) throws Exception {
		String body = """
				{"title":"%s","price":%d,"stock":%d,"category":"文具","description":"用例","sellerName":"测试"}
				""".formatted(title, price, stock);

		String response = mockMvc.perform(post("/api/products")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response).path("data").path("id").asLong();
	}
}
