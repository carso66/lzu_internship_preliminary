package com.lzu.market;

import com.lzu.market.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public ApiResponse<Void> health() {
        return ApiResponse.ok();
    }
}
