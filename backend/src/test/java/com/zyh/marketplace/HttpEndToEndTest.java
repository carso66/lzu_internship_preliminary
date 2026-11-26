package com.zyh.marketplace;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * 真实 HTTP 端到端测试：随机端口启动内嵌 Tomcat，用 java.net.http.HttpClient 发真实请求。
 *
 * <p><b>为什么需要这个类</b>：MockMvc 不经过 Servlet 容器的查询串解析，
 * 它按 ISO-8859-1 解析 URI 里的查询串，直接把中文的 URL 编码写进 URI 会变成乱码。
 * 「中文 keyword 能否被正确接收」只有真实 Tomcat 才能验证，因此单独用真实 HTTP 覆盖：
 *  1. 中文等非 ASCII 查询参数在真实 Tomcat + UTF-8 解码下可用；
 *  2. 非法查询串编码返回 400 而不是 500；
 *  3. 真实网络下的状态码与统一响应结构。
 *
 * <p>数据源仍是隔离的内存库（application-test.yaml），与本地文件库无关。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HttpEndToEndTest extends ApiTestSupport {

    private final HttpClient http = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int port;

    private HttpResponse<String> getRaw(String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("真实 HTTP：中文 keyword 查询命中")
    void realHttpChineseKeyword() throws Exception {
        truncateProducts();
        insertProductDirectly("校园好物笔记本", 1990L, 3, "文具", "on", "店铺", "2026-01-01 10:00:00");
        insertProductDirectly("蓝牙耳机", 8990L, 1, "数码", "on", "店铺", "2026-01-01 11:00:00");

        HttpResponse<String> resp = getRaw("/api/products?keyword=" + enc("笔记本"));
        assertThat(resp.statusCode()).isEqualTo(200);
        Object total = JsonPath.parse(resp.body()).read("$.data.total");
        assertThat(((Number) total).intValue()).isEqualTo(1);

        HttpResponse<String> byCategory = getRaw("/api/products?category=" + enc("数码"));
        assertThat(byCategory.statusCode()).isEqualTo(200);
        assertThat(((Number) JsonPath.parse(byCategory.body()).read("$.data.total")).intValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("真实 HTTP：非法查询串编码返回 400 + 40001（不是 500）")
    void realHttpBrokenEncodingIsClientError() throws Exception {
        HttpResponse<String> resp = getRaw("/api/products?keyword=%B2%E2%CA%D4");
        assertThat(resp.statusCode()).isEqualTo(400);
        assertThat(((Number) JsonPath.parse(resp.body()).read("$.code")).intValue()).isEqualTo(40001);
    }

    @Test
    @DisplayName("真实 HTTP：health / 列表 / 详情 / 404 状态码正确")
    void realHttpBasicRoutes() throws Exception {
        truncateProducts();
        long id = insertProductDirectly("商品", 100L, 1, "文具", "on", "店铺", "2026-01-01 10:00:00");

        assertThat(getRaw("/api/health").statusCode()).isEqualTo(200);
        assertThat(getRaw("/api/products").statusCode()).isEqualTo(200);
        assertThat(getRaw("/api/products/" + id).statusCode()).isEqualTo(200);
        HttpResponse<String> notFound = getRaw("/api/products/999999");
        assertThat(notFound.statusCode()).isEqualTo(404);
        assertThat(((Number) JsonPath.parse(notFound.body()).read("$.code")).intValue()).isEqualTo(40401);
    }
}