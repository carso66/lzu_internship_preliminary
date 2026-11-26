package com.example.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 覆盖 L1-6 下单与参数校验。
 *
 * 每条用例用随机 token 造自己的商品，互不干扰；手机号也随机，
 * 避免列表接口把别的用例订单也算进来。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderCreateTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void createOrderReturns201AndDeductsStock() throws Exception {
		long productId = createProduct(uniqueToken() + " 下单", 1990, 10);
		String phone = uniquePhone();

		String response = mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(orderBody(productId, 3, phone)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.productId").value(productId))
			.andExpect(jsonPath("$.data.quantity").value(3))
			.andExpect(jsonPath("$.data.unitPrice").value(1990))
			.andExpect(jsonPath("$.data.totalAmount").value(5970))
			.andExpect(jsonPath("$.data.status").value("created"))
			.andExpect(jsonPath("$.data.buyerPhone").value(phone))
			.andExpect(jsonPath("$.data.orderNo").value(org.hamcrest.Matchers.startsWith("SO")))
			.andReturn().getResponse().getContentAsString();

		String orderNo = objectMapper.readTree(response).path("data").path("orderNo").asText();
		assertThat(orderNo).hasSize(14); // SO + 8 位日期 + 4 位序号

		// 库存从 10 扣到 7
		mockMvc.perform(get("/api/products/{id}", productId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stock").value(7));
	}

	@Test
	void createOrderIgnoresClientSuppliedPriceAndStatus() throws Exception {
		long productId = createProduct(uniqueToken() + " 改价", 5000, 5);
		String phone = uniquePhone();

		// 客户端谎报单价 1 分、总额 1 分、状态 paid，都应被忽略
		String body = """
				{"productId":%d,"quantity":2,"buyerName":"张三","buyerPhone":"%s","address":"兰州市城关区天水南路222号",
				 "unitPrice":1,"totalAmount":1,"status":"paid"}
				""".formatted(productId, phone);

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.unitPrice").value(5000))
			.andExpect(jsonPath("$.data.totalAmount").value(10000))
			.andExpect(jsonPath("$.data.status").value("created"));
	}

	@Test
	void createOrderSnapshotsProductTitle() throws Exception {
		String title = uniqueToken() + " 快照";
		long productId = createProduct(title, 800, 5);

		String response = mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(orderBody(productId, 1, uniquePhone())))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();

		assertThat(objectMapper.readTree(response).path("data").path("productTitle").asText()).isEqualTo(title);
	}

	@Test
	void createOrderReturns409WhenStockNotEnough() throws Exception {
		long productId = createProduct(uniqueToken() + " 缺货", 1000, 2);

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(orderBody(productId, 3, uniquePhone())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(40901))
			.andExpect(jsonPath("$.message").value("库存不足"))
			.andExpect(jsonPath("$.data").isEmpty());

		// 下单失败不能扣库存
		mockMvc.perform(get("/api/products/{id}", productId))
			.andExpect(jsonPath("$.data.stock").value(2));
	}

	@Test
	void createOrderReturns409WhenProductOffShelf() throws Exception {
		long productId = createProduct(uniqueToken() + " 下架", 1000, 10);
		changeStatus(productId, "off");

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(orderBody(productId, 1, uniquePhone())))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value(40902))
			.andExpect(jsonPath("$.message").value("商品已下架"));

		mockMvc.perform(get("/api/products/{id}", productId))
			.andExpect(jsonPath("$.data.stock").value(10));
	}

	@Test
	void createOrderReturns404WhenProductMissing() throws Exception {
		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(orderBody(999_999, 1, uniquePhone())))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(40401))
			.andExpect(jsonPath("$.message").value("商品不存在"));
	}

	@Test
	void createOrderRejectsNonPositiveQuantity() throws Exception {
		long productId = createProduct(uniqueToken() + " 数量", 1000, 10);

		// 0、-1、字符串 "2"、浮点 2.5、缺失、null 全部 400
		for (String quantity : new String[] { "0", "-1", "\"2\"", "2.5", "null" }) {
			assertBadRequest("""
					{"productId":%d,"quantity":%s,"buyerName":"张三","buyerPhone":"%s","address":"兰州市"}
					""".formatted(productId, quantity, uniquePhone()),
					"quantity 必须是大于等于 1 的整数");
		}
		assertBadRequest("""
				{"productId":%d,"buyerName":"张三","buyerPhone":"%s","address":"兰州市"}
				""".formatted(productId, uniquePhone()),
				"quantity 必须是大于等于 1 的整数");
	}

	@Test
	void createOrderRejectsInvalidProductId() throws Exception {
		for (String productId : new String[] { "0", "-3", "\"1\"", "1.5" }) {
			assertBadRequest("""
					{"productId":%s,"quantity":1,"buyerName":"张三","buyerPhone":"%s","address":"兰州市"}
					""".formatted(productId, uniquePhone()),
					"productId 必须为正整数");
		}
	}

	@Test
	void createOrderRejectsInvalidBuyerFields() throws Exception {
		long productId = createProduct(uniqueToken() + " 买家", 1000, 10);

		String template = """
				{"productId":%d,"quantity":1,"buyerName":%s,"buyerPhone":%s,"address":%s}
				""";

		// 收货人姓名
		assertBadRequest(template.formatted(productId, "\"\"", "\"" + uniquePhone() + "\"", "\"兰州市\""),
				"收货人姓名不能为空，且不能超过 50 个字符");
		assertBadRequest(template.formatted(productId, "123", "\"" + uniquePhone() + "\"", "\"兰州市\""),
				"收货人姓名不能为空，且不能超过 50 个字符");

		// 手机号：位数不对、含非数字
		assertBadRequest(template.formatted(productId, "\"张三\"", "\"1380013800\"", "\"兰州市\""),
				"手机号需为 11 位数字");
		assertBadRequest(template.formatted(productId, "\"张三\"", "\"1380013800a\"", "\"兰州市\""),
				"手机号需为 11 位数字");

		// 收货地址
		assertBadRequest(template.formatted(productId, "\"张三\"", "\"" + uniquePhone() + "\"", "\"\""),
				"收货地址不能为空，且不能超过 255 个字符");
	}

	@Test
	void createOrderRejectsNonObjectBody() throws Exception {
		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("[1,2,3]"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001));
	}

	@Test
	void repeatedOrdersFromOneBuyerGetDistinctOrderNumbers() throws Exception {
		long productId = createProduct(uniqueToken() + " 多单", 100, 10);
		String phone = uniquePhone();

		String first = placeOrder(productId, 1, phone);
		String second = placeOrder(productId, 1, phone);

		assertThat(first).isNotEqualTo(second);
	}

	private String placeOrder(long productId, int quantity, String phone) throws Exception {
		String response = mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(orderBody(productId, quantity, phone)))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response).path("data").path("orderNo").asText();
	}

	private String orderBody(long productId, int quantity, String phone) {
		return """
				{"productId":%d,"quantity":%d,"buyerName":"张三","buyerPhone":"%s","address":"兰州市城关区天水南路222号"}
				""".formatted(productId, quantity, phone);
	}

	private void assertBadRequest(String body, String expectedMessage) throws Exception {
		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001))
			.andExpect(jsonPath("$.message").value(expectedMessage))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	private static String uniqueToken() {
		return "t" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	/** 随机 11 位手机号，让每条用例的订单只属于自己。 */
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

	private void changeStatus(long id, String status) throws Exception {
		mockMvc.perform(patch("/api/products/{id}/status", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"" + status + "\"}"))
			.andExpect(status().isOk());
	}
}
