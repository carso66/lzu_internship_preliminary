package com.example.demo.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
class ProductCreateTests {

	private static final String VALID_BODY = """
			{"title":"考研数学复习全书","price":4990,"stock":10,"category":"图书",
			 "description":"九成新，无笔记","sellerName":"兰大旧书店"}
			""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ProductRepository productRepository;

	@Test
	void createsProductAndReturns201() throws Exception {
		String response = mockMvc.perform(post("/api/products")
				.contentType(MediaType.APPLICATION_JSON)
				.content(VALID_BODY))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value(0))
			.andExpect(jsonPath("$.message").value("ok"))
			.andExpect(jsonPath("$.data.title").value("考研数学复习全书"))
			.andExpect(jsonPath("$.data.price").value(4990))
			.andExpect(jsonPath("$.data.stock").value(10))
			.andExpect(jsonPath("$.data.category").value("图书"))
			.andExpect(jsonPath("$.data.description").value("九成新，无笔记"))
			.andExpect(jsonPath("$.data.status").value("on"))
			.andExpect(jsonPath("$.data.sellerName").value("兰大旧书店"))
			.andExpect(jsonPath("$.data.createdAt").isNotEmpty())
			.andReturn().getResponse().getContentAsString();

		JsonNode data = objectMapper.readTree(response).path("data");
		assertThat(data.path("id").asLong()).isPositive();

		assertThat(productRepository.findById(data.path("id").asLong())).hasValueSatisfying(product -> {
			assertThat(product.title()).isEqualTo("考研数学复习全书");
			assertThat(product.status()).isEqualTo("on");
			assertThat(product.price()).isEqualTo(4990);
			assertThat(product.sellerName()).isEqualTo("兰大旧书店");
		});
	}

	@Test
	void trimsTitleAndTreatsMissingDescriptionAsEmpty() throws Exception {
		mockMvc.perform(post("/api/products")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"  二手台灯  ","price":1,"stock":0,"category":"文具","sellerName":" 我 "}
						"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.title").value("二手台灯"))
			.andExpect(jsonPath("$.data.description").value(""))
			.andExpect(jsonPath("$.data.stock").value(0))
			.andExpect(jsonPath("$.data.sellerName").value("我"));
	}

	@Test
	void treatsExplicitNullDescriptionAsEmpty() throws Exception {
		mockMvc.perform(post("/api/products")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"title":"显式 null 描述","price":100,"stock":1,"category":"文具","description":null,"sellerName":"我"}
						"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.data.description").value(""));
	}

	@Test
	void rejectsNonPositiveOrNonIntegerPrice() throws Exception {
		assertBadRequest("""
				{"title":"商品","price":0,"stock":1,"category":"文具","sellerName":"我"}
				""", "价格必须为正整数（单位：分）");
		assertBadRequest("""
				{"title":"商品","price":-100,"stock":1,"category":"文具","sellerName":"我"}
				""", "价格必须为正整数（单位：分）");
		assertBadRequest("""
				{"title":"商品","price":19.9,"stock":1,"category":"文具","sellerName":"我"}
				""", "价格必须为正整数（单位：分）");
		assertBadRequest("""
				{"title":"商品","price":"1990","stock":1,"category":"文具","sellerName":"我"}
				""", "价格必须为正整数（单位：分）");
	}

	@Test
	void rejectsBlankOrTooLongTitle() throws Exception {
		assertBadRequest("""
				{"title":"   ","price":100,"stock":1,"category":"文具","sellerName":"我"}
				""", "标题需为 1~50 个字符");
		assertBadRequest("""
				{"title":"%s","price":100,"stock":1,"category":"文具","sellerName":"我"}
				""".formatted("字".repeat(51)), "标题需为 1~50 个字符");
	}

	@Test
	void rejectsStockOutOfRange() throws Exception {
		assertBadRequest("""
				{"title":"商品","price":100,"stock":-1,"category":"文具","sellerName":"我"}
				""", "库存需为 0~999 的整数");
		assertBadRequest("""
				{"title":"商品","price":100,"stock":1000,"category":"文具","sellerName":"我"}
				""", "库存需为 0~999 的整数");
		assertBadRequest("""
				{"title":"商品","price":100,"stock":1.5,"category":"文具","sellerName":"我"}
				""", "库存需为 0~999 的整数");
	}

	@Test
	void rejectsUnknownCategory() throws Exception {
		assertBadRequest("""
				{"title":"商品","price":100,"stock":1,"category":"电器","sellerName":"我"}
				""", null);
	}

	@Test
	void rejectsTooLongDescription() throws Exception {
		assertBadRequest("""
				{"title":"商品","price":100,"stock":1,"category":"文具","description":"%s","sellerName":"我"}
				""".formatted("字".repeat(201)), "描述不能超过 200 字");
	}

	@Test
	void rejectsMissingSellerName() throws Exception {
		assertBadRequest("""
				{"title":"商品","price":100,"stock":1,"category":"文具"}
				""", "卖家名不能为空");
	}

	@Test
	void rejectsMalformedJson() throws Exception {
		mockMvc.perform(post("/api/products")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40002))
			.andExpect(jsonPath("$.data").isEmpty());
	}

	private void assertBadRequest(String body, String expectedMessage) throws Exception {
		ResultActions actions = mockMvc.perform(post("/api/products")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(40001))
			.andExpect(jsonPath("$.data").isEmpty());
		if (expectedMessage != null) {
			actions.andExpect(jsonPath("$.message").value(expectedMessage));
		}
	}
}
