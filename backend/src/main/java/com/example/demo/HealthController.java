package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 0);
        result.put("message", "ok");
        result.put("data", null);
        return result;
    }
}