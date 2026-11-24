package cn.edu.lzu.marketplace.controller;

import cn.edu.lzu.marketplace.common.BusinessException;
import cn.edu.lzu.marketplace.common.ErrorCode;
import cn.edu.lzu.marketplace.dto.CreateOrderRequest;
import cn.edu.lzu.marketplace.dto.OrderPageResponse;
import cn.edu.lzu.marketplace.dto.OrderResponse;
import cn.edu.lzu.marketplace.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 订单接口（L1）的 Web 层集成测试：加载完整上下文（含 JacksonConfig 严格整数
 * 反序列化与 GlobalExceptionHandler），仅 Mock 掉 OrderService 隔离业务层。
 *
 * <p>说明：本类不接触真实数据库（无 schema.sql），Repository SQL 属待联调项。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private static final OrderResponse SAMPLE = new OrderResponse(
            1L, "SO202611190001", 3L, "考研数学复习全书", 2, 4990L, 9980L, "created",
            "张三", "13800138000", "兰州市城关区天水南路222号",
            LocalDateTime.of(2026, 11, 19, 15, 0, 0));

    private static final String VALID_BODY =
            "{\"productId\":3,\"quantity\":2,\"buyerName\":\"张三\","
                    + "\"buyerPhone\":\"13800138000\",\"address\":\"兰州市城关区天水南路222号\"}";

    // ---------- POST /api/orders ----------

    @Test
    void createReturns201WithServerComputedAmount() throws Exception {
        when(orderService.create(any(CreateOrderRequest.class))).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.orderNo").value("SO202611190001"))
                .andExpect(jsonPath("$.data.unitPrice").value(4990))
                .andExpect(jsonPath("$.data.totalAmount").value(9980))
                .andExpect(jsonPath("$.data.status").value("created"));
    }

    @Test
    void createMissingQuantityReturns400And40001() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":3,\"buyerName\":\"张三\","
                                + "\"buyerPhone\":\"13800138000\",\"address\":\"地址\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("quantity")));
    }

    @Test
    void createZeroQuantityReturns400And40001() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":3,\"quantity\":0,\"buyerName\":\"张三\","
                                + "\"buyerPhone\":\"13800138000\",\"address\":\"地址\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("quantity")));
    }

    @Test
    void createNegativeQuantityReturns400And40001() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":3,\"quantity\":-1,\"buyerName\":\"张三\","
                                + "\"buyerPhone\":\"13800138000\",\"address\":\"地址\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void createQuantityAsNumericStringReturns400And40001() throws Exception {
        // 字符串形式的数字不应被当成合法整数（JacksonConfig 严格反序列化）
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":3,\"quantity\":\"2\",\"buyerName\":\"张三\","
                                + "\"buyerPhone\":\"13800138000\",\"address\":\"地址\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void createQuantityAsDecimalReturns400And40001() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":3,\"quantity\":2.5,\"buyerName\":\"张三\","
                                + "\"buyerPhone\":\"13800138000\",\"address\":\"地址\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void createStockInsufficientReturns409And40901() throws Exception {
        doThrow(new BusinessException(ErrorCode.STOCK_INSUFFICIENT, "库存不足"))
                .when(orderService).create(any(CreateOrderRequest.class));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901))
                .andExpect(jsonPath("$.message").value("库存不足"));
    }

    @Test
    void createProductOffSaleReturns409And40902() throws Exception {
        doThrow(new BusinessException(ErrorCode.PRODUCT_OFF_SALE, "商品已下架"))
                .when(orderService).create(any(CreateOrderRequest.class));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));
    }

    @Test
    void createProductNotFoundReturns404And40401() throws Exception {
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"))
                .when(orderService).create(any(CreateOrderRequest.class));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ---------- GET /api/orders/{orderNo} ----------

    @Test
    void getByOrderNoReturnsOrder() throws Exception {
        when(orderService.getByOrderNo("SO202611190001")).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/orders/SO202611190001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value("SO202611190001"))
                .andExpect(jsonPath("$.data.totalAmount").value(9980));
    }

    @Test
    void getByOrderNoNotFoundReturns404And40401() throws Exception {
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "订单不存在"))
                .when(orderService).getByOrderNo("SO999999999999");

        mockMvc.perform(get("/api/orders/SO999999999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("订单不存在"));
    }

    // ---------- GET /api/orders?buyerPhone=... ----------

    @Test
    void listReturnsTotalPagePageSizeAndItems() throws Exception {
        OrderPageResponse page = new OrderPageResponse(1L, 1, 10, List.of(SAMPLE));
        when(orderService.list(eq(1), eq(10), eq("13800138000"))).thenReturn(page);

        mockMvc.perform(get("/api/orders").param("buyerPhone", "13800138000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items[0].orderNo").value("SO202611190001"));
    }

    @Test
    void listForwardsPagination() throws Exception {
        OrderPageResponse page = new OrderPageResponse(0L, 2, 20, List.of());
        when(orderService.list(eq(2), eq(20), eq("13800138000"))).thenReturn(page);

        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", "13800138000")
                        .param("page", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.pageSize").value(20));
    }

    @Test
    void listMissingBuyerPhoneReturns400And40001() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("buyerPhone")));
    }

    @Test
    void listPageSizeOver50Returns400And40001() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", "13800138000")
                        .param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("pageSize")));
    }

    @Test
    void listPageBelow1Returns400And40001() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .param("buyerPhone", "13800138000")
                        .param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }
}
