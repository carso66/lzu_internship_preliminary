package com.lzu.marketplace.health;

import com.lzu.marketplace.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查。用于确认服务已启动、统一响应结构生效。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * GET /api/health → 200 {"code":0,"message":"ok","data":null}
     */
    @GetMapping("/health")
    public ApiResponse<Void> health() {
        return ApiResponse.ok(null);
    }
}
