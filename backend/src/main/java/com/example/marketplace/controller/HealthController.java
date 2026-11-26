package com.example.marketplace.controller;

import com.example.marketplace.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查接口，用于确认应用和数据库连接是否可用。
 */
@RestController
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/api/health")
    public ResponseEntity<HealthResponse> health() {
        if (healthService.isDatabaseReady()) {
            return ResponseEntity.ok(new HealthResponse(0, "ok", null));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new HealthResponse(50000, "database unavailable", null));
    }

    public record HealthResponse(int code, String message, Object data) {
    }
}
