package com.lzu.marketplace.controller;

import com.lzu.marketplace.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供最小健康检查，用于确认 HTTP 服务已启动，不执行数据库查询。 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Void> health() {
        return ApiResponse.success(null);
    }
}
