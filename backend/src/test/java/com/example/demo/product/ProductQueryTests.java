package com.example.demo.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * 覆盖 L0-3 / L0-4 / L0-5。
 *
 * 测试之间共享同一个内存库，所以每条用例都用随机关键字圈定自己创建的商品，
 * 不依赖全表条数。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductQueryTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void listReturnsEmptyPageWhenNothingMatches() throws Exception {
		String keyword = uniqueToken();

		mockMvc.perform(get("/api/products").param("keyword", keyword))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.total").value(0))
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.pageSize").value(10))
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void listSplitsPagesAndReportsFilteredTotal() throws Exception {
		String keyword = uniqueToken();
		createProduct(keyword + " 之一", "文具", 100);
		createProduct(keyword + " 之二", "文具", 200);
		createProduct(keyword + " 之三", "文具", 300);

		mockMvc.perform(get("/api/products")
				.param("keyword", keyword)
				.param("page", "1")
				.param("pageSize", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.page").value(1))
			.andExpect(jsonPath("$.data.pageSize").value(2))
			.andExpect(jsonPath("$.data.items.length()").value(2));

		mockMvc.perform(get("/api/products")
				.param("keyword", keyword)
				.param("page", "2")
				.param("pageSize", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.items.length()").value(1));

		// 越界页返回空数组，但 total 仍是筛选后的总数
		mockMvc.perform(get("/api/products")
				.param("keyword", keyword)
				.param("page", "3")
				.param("pageSize", "2"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(3))
			.andExpect(jsonPath("$.data.items").isEmpty());
	}

	@Test
	void listOrdersByIdAscending() throws Exception {
		String keyword = uniqueToken();
		long first = createProduct(keyword + " A", "文具", 100);
		long second = createProduct(keyword + " B", "文具", 200);

		String response = mockMvc.perform(get("/api/products").param("keyword", keyword))
			.andExpect(status().isOk())
			.andReturn().getResponse().getContentAsString();

		JsonNode items = objectMapper.readTree(response).path("data").path("items");
		List<Long> ids = new ArrayList<>();
		items.forEach(item -> ids.add(item.path("id").asLong()));
		assertThat(ids).containsExactly(first, second);
	}

	@Test
	void listCombinesKeywordStatusAndCategory() throws Exception {
		String keyword = uniqueToken();
		long keep = createProduct(keyword + " 在售图书", "图书", 1000);
		long wrongCategory = createProduct(keyword + " 在售文具", "文具", 1000);
		long offShelf = createProduct(keyword + " 已下架图书", "图书", 1000);
		changeStatus(offShelf, "off");

		String response = mockMvc.perform(get("/api/products")
				.param("keyword", keyword)
				.param("status", "on")
				.param("category", "图书"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(1))
			.andExpect(jsonPath("$.data.items.length()").value(1))
			.andReturn().getResponse().getContentAsString();

		JsonNode item = objectMapper.readTree(response).path("data").path("items").get(0);
		assertThat(item.path("id").asLong()).isEqualTo(keep);
		assertThat(item.path("id").asLong()).isNotEqualTo(wrongCategory);

		// 同一关键字下换 status=off 只应命中被下架的那条
		mockMvc.perform(get("/api/products")
				.param("keyword", keyword)
				.param("status", "off"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(1))
			.andExpect(jsonPath("$.data.items[0].id").value(offShelf));
	}

	@Test
	void listMatchesKeywordCaseInsensitivelyAndLiterally() throws Exception {
		String token = uniqueToken();
		long id = createProduct(token + "ABC", "文具", 100);

		mockMvc.perform(get("/api/products").param("keyword", token.toLowerCase() + "abc"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.items[0].id").value(id));

		// "%" 应被当作普通字符，不能当成通配符匹配到所有商品
		mockMvc.perform(get("/api/products").param("keyword", "%"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(0));
	}

	@Test
	void listRejectsInvalidPagingAndStatus() throws Exception {
		mockMvc.perform(get("/api/products").param("pageSize", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001));
		mockMvc.perform(get("/api/products").param("pageSize", "51"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001));
		mockMvc.perform(get("/api/products").param("page", "0"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001));
		mockMvc.perform(get("/api/products").param("status", "ON"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001));
		// 非整数的 page 也应是 400，不能变成 500
		mockMvc.perform(get("/api/products").param("page", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001));
	}

	@Test
	void listAcceptsMaxPageSize() throws Exception {
		mockMvc.perform(get("/api/products").param("pageSize", "50"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.pageSize").value(50));
	}

	@Test
	void detailReturnsProduct() throws Exception {
		long id = createProduct(uniqueToken() + " 详情", "图书", 1234);

		mockMvc.perform(get("/api/products/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.id").value(id))
			.andExpect(jsonPath("$.data.price").value(1234))
			.andExpect(jsonPath("$.data.stock").value(5))
			.andExpect(jsonPath("$.data.category").value("图书"))
			.andExpect(jsonPath("$.data.status").value("on"))
			.andExpect(jsonPath("$.data.createdAt").isNotEmpty());
	}

	@Test
	void detailReturns404ForUnknownId() throws Exception {
		mockMvc.perform(get("/api/products/{id}", 999_999))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(40401))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	void detailReturns400ForNonNumericId() throws Exception {
		mockMvc.perform(get("/api/products/{id}", "abc"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	void updateStatusTogglesOnAndOff() throws Exception {
		long id = createProduct(uniqueToken() + " 上下架", "文具", 500);

		mockMvc.perform(patch("/api/products/{id}/status", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"off\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.data.id").value(id))
			.andExpect(jsonPath("$.data.status").value("off"));

		mockMvc.perform(get("/api/products/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("off"));

		mockMvc.perform(patch("/api/products/{id}/status", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"on\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("on"));

		mockMvc.perform(get("/api/products/{id}", id))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value("on"));
	}

	@Test
	void updateStatusRejectsInvalidBody() throws Exception {
		long id = createProduct(uniqueToken() + " 非法状态", "文具", 500);

		assertStatusBadRequest(id, "{\"status\":\"ON\"}");
		assertStatusBadRequest(id, "{\"status\":\"abc\"}");
		assertStatusBadRequest(id, "{\"status\":\"\"}");
		assertStatusBadRequest(id, "{\"status\":1}");
		assertStatusBadRequest(id, "{}");
	}

	@Test
	void updateStatusReturns404ForUnknownId() throws Exception {
		mockMvc.perform(patch("/api/products/{id}/status", 999_999)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"status\":\"off\"}"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value(40401))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	@Test
	void publishThenListThenOffShelfThenQueryWorks() throws Exception {
		String keyword = uniqueToken() + " 全流程";
		long id = createProduct(keyword, "数码", 8800);

		mockMvc.perform(get("/api/products").param("keyword", keyword))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(1))
			.andExpect(jsonPath("$.data.items[0].status").value("on"));

		changeStatus(id, "off");

		mockMvc.perform(get("/api/products")
				.param("keyword", keyword)
				.param("status", "on"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(0));

		mockMvc.perform(get("/api/products")
				.param("keyword", keyword)
				.param("status", "off"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.total").value(1))
			.andExpect(jsonPath("$.data.items[0].id").value(id));
	}

	private static String uniqueToken() {
		return "t" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
	}

	private long createProduct(String title, String category, int price) throws Exception {
		String body = """
				{"title":"%s","price":%d,"stock":5,"category":"%s","description":"用例","sellerName":"测试"}
				""".formatted(title, price, category);

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
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.status").value(status));
	}

	private void assertStatusBadRequest(long id, String body) throws Exception {
		mockMvc.perform(patch("/api/products/{id}/status", id)
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001))
			.andExpect(jsonPath("$.message").value("status 只能是 on 或 off"))
			.andExpect(jsonPath("$.data").isEmpty());
	}
}
