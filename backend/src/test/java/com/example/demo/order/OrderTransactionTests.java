package com.example.demo.order;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 验证「扣库存与插订单在同一事务」这条硬性要求。
 *
 * 用 Spy 让插入订单必然失败，再检查库存没有被扣掉。
 * 如果 Service 上漏了 @Transactional，扣库存会单独提交，库存就永久少了。
 *
 * 这个类会新建一个应用上下文（Spy 改变了上下文缓存键），
 * 所以只放这一条断言，不把其他下单用例搬过来。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderTransactionTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@SpyBean
	private OrderRepository orderRepository;

	@Test
	void stockIsRolledBackWhenInsertingOrderFails() throws Exception {
		long productId = createProduct(5);

		doThrow(new IllegalStateException("模拟插单失败"))
			.when(orderRepository).insert(any(OrderCreateRequest.class), anyString(), anyString(), anyInt());

		mockMvc.perform(post("/api/orders")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"productId":%d,"quantity":3,"buyerName":"张三","buyerPhone":"13800138000","address":"兰州市"}
						""".formatted(productId)))
			.andExpect(status().isInternalServerError())
			.andExpect(jsonPath("$.code").value(50000));

		// 库存仍是 5，说明扣减被回滚了
		mockMvc.perform(get("/api/products/{id}", productId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.stock").value(5));
	}

	private long createProduct(int stock) throws Exception {
		String title = "t" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10) + " 事务";
		String body = """
				{"title":"%s","price":1000,"stock":%d,"category":"文具","description":"用例","sellerName":"测试"}
				""".formatted(title, stock);

		String response = mockMvc.perform(post("/api/products")
				.contentType(MediaType.APPLICATION_JSON)
				.content(body))
			.andExpect(status().isCreated())
			.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response).path("data").path("id").asLong();
	}
}
