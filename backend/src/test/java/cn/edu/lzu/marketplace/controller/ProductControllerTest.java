package cn.edu.lzu.marketplace.controller;

import cn.edu.lzu.marketplace.common.BusinessException;
import cn.edu.lzu.marketplace.common.ErrorCode;
import cn.edu.lzu.marketplace.dto.ProductCreateRequest;
import cn.edu.lzu.marketplace.dto.ProductPageResponse;
import cn.edu.lzu.marketplace.dto.ProductResponse;
import cn.edu.lzu.marketplace.dto.ProductStatusRequest;
import cn.edu.lzu.marketplace.dto.ProductStatusResponse;
import cn.edu.lzu.marketplace.service.ProductService;
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
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品接口（L0）的 Web 层集成测试：加载完整上下文（含 JacksonConfig 严格整数
 * 反序列化与 GlobalExceptionHandler），仅 Mock 掉 ProductService 隔离业务层。
 *
 * <p>通过 ProductService 测试替身控制业务返回，断言覆盖 HTTP 响应与参数传递。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private static final ProductResponse SAMPLE = new ProductResponse(
            13L, "测试商品", 1990L, 10, "文具", "备注", "on", "校园数码铺",
            LocalDateTime.of(2026, 11, 24, 15, 0, 0));

    // ① 发布商品：核对创建响应和请求体字段约束。

    @Test
    void createReturns201WithIdAndDefaultStatus() throws Exception {
        when(productService.create(any(ProductCreateRequest.class))).thenReturn(SAMPLE);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试商品\",\"price\":1990,\"stock\":10,"
                                + "\"category\":\"文具\",\"description\":\"备注\",\"sellerName\":\"校园数码铺\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("ok"))
                .andExpect(jsonPath("$.data.id").value(13))
                .andExpect(jsonPath("$.data.title").value("测试商品"))
                .andExpect(jsonPath("$.data.price").value(1990))
                .andExpect(jsonPath("$.data.status").value("on"));
    }

    @Test
    void createMissingTitleReturns400And40001() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":1990,\"stock\":10,\"category\":\"文具\",\"sellerName\":\"校园数码铺\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("title")));
    }

    @Test
    void createNegativePriceReturns400And40001() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试商品\",\"price\":-1,\"stock\":10,"
                                + "\"category\":\"文具\",\"sellerName\":\"校园数码铺\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void createPriceAsNumericStringReturns400And40001() throws Exception {
        // 字符串形式的数字不应被当成合法整数（JacksonConfig 严格反序列化）
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试商品\",\"price\":\"1990\",\"stock\":10,"
                                + "\"category\":\"文具\",\"sellerName\":\"校园数码铺\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void createPriceAsDecimalReturns400And40001() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试商品\",\"price\":1990.5,\"stock\":10,"
                                + "\"category\":\"文具\",\"sellerName\":\"校园数码铺\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001));
    }

    @Test
    void createMissingStockReturns400And40001() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试商品\",\"price\":1990,"
                                + "\"category\":\"文具\",\"sellerName\":\"校园数码铺\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("stock")));
    }

    // ② 商品列表：核对组合筛选、分页字段和查询参数校验。

    @Test
    void listReturnsTotalPagePageSizeAndItems() throws Exception {
        ProductPageResponse page = new ProductPageResponse(0L, 1, 10, List.of());
        when(productService.list(eq(1), eq(10), isNull(), isNull(), isNull())).thenReturn(page);

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    void listForwardsCombinedFilters() throws Exception {
        ProductPageResponse page = new ProductPageResponse(1L, 2, 20, List.of(SAMPLE));
        when(productService.list(eq(2), eq(20), eq("考研"), eq("on"), eq("文具"))).thenReturn(page);

        mockMvc.perform(get("/api/products")
                        .param("keyword", "考研")
                        .param("status", "on")
                        .param("category", "文具")
                        .param("page", "2")
                        .param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(13));
    }

    @Test
    void listPageSizeOver50Returns400And40001() throws Exception {
        mockMvc.perform(get("/api/products").param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("pageSize")));
    }

    @Test
    void listPageBelow1Returns400And40001() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("page")));
    }

    @Test
    void listInvalidStatusReturns400And40001() throws Exception {
        mockMvc.perform(get("/api/products").param("status", "unknown"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("status")));
    }

    // ③ 商品详情：覆盖按 ID 查询及资源不存在。

    @Test
    void getByIdReturnsProduct() throws Exception {
        when(productService.getById(13L)).thenReturn(SAMPLE);

        mockMvc.perform(get("/api/products/13"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(13))
                .andExpect(jsonPath("$.data.title").value("测试商品"));
    }

    @Test
    void getByIdNotFoundReturns404And40401() throws Exception {
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"))
                .when(productService).getById(999L);

        mockMvc.perform(get("/api/products/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401))
                .andExpect(jsonPath("$.message").value("商品不存在"));
    }

    // ④ 商品状态：覆盖上下架接口的响应、状态枚举校验和资源缺失。

    @Test
    void updateStatusReturns200WithIdAndStatus() throws Exception {
        when(productService.updateStatus(eq(1L), any(ProductStatusRequest.class)))
                .thenReturn(new ProductStatusResponse(1L, "off"));

        mockMvc.perform(patch("/api/products/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"off\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("off"));
    }

    @Test
    void updateStatusInvalidValueReturns400And40001() throws Exception {
        mockMvc.perform(patch("/api/products/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"unknown\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40001))
                .andExpect(jsonPath("$.message").value(containsString("status")));
    }

    @Test
    void updateStatusNotFoundReturns404And40401() throws Exception {
        doThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "商品不存在"))
                .when(productService).updateStatus(eq(999L), any(ProductStatusRequest.class));

        mockMvc.perform(patch("/api/products/999/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"off\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }
}
