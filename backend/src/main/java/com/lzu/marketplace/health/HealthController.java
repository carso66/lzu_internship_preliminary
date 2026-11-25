package com.lzu.marketplace.health;

import com.lzu.marketplace.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口，用来确认工程能正常启动和对外提供 HTTP 服务。
 */
@RestController
@RequestMapping("/api")
public class HealthController {

    /**
     * GET /api/health
     *
     * <p>返回 200 和 {"code":0,"message":"ok","data":null}。</p>
     */
    @GetMapping("/health")
    public ApiResponse<Void> health() {
        return ApiResponse.ok();
    }
}
