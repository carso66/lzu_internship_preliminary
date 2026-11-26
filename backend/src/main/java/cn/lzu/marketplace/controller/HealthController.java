package cn.lzu.marketplace.controller;

import cn.lzu.marketplace.common.Result;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 健康检查。
 * 特意去查了一次 products 表：能返回条数，说明
 *   ① 服务起来了 ② H2 连上了 ③ schema.sql 执行成功。
 * 这三件事任何一件不对，这里都会报 500，比只看端口通不通有用。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        Integer productCount = jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "up");
        data.put("products", productCount);
        return Result.ok(data);
    }
}
