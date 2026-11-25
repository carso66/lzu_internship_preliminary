package cn.edu.lzu.marketplace.controller;

import cn.edu.lzu.marketplace.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供 HTTP 健康检查，供调用方确认应用已启动并能够响应请求。 */
@RestController
public class HealthController {

    /**
     * 健康检查：GET /api/health → {"code":0,"message":"ok","data":null}
     */
    @GetMapping("/api/health")
    public ApiResponse<Void> health() {
        return ApiResponse.ok();
    }
}
