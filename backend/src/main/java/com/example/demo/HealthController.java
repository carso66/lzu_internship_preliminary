package com.example.demo;

import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	@GetMapping("/api/health")
	public Map<String, Object> health() {
		Map<String, Object> response = new HashMap<>();
		response.put("code", 0);
		response.put("message", "ok");
		response.put("data", null);
		return response;
	}
}
