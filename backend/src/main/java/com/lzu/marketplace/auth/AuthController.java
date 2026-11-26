package com.lzu.marketplace.auth;

import com.lzu.marketplace.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 登录接口（官方 §6 L2-11）。
 *
 * <p>账号写死在 {@link AuthService}，不接数据库、不建用户表。
 * 成功 200，{@code data.token} 为随机 token；失败 400 / 40001，
 * 统一由 {@code GlobalExceptionHandler} 兜住。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login —— 换取 Bearer token。
     *
     * <p>题目不要求 JWT / 过期 / 角色，token 只是一个服务端内存里的随机字符串，
     * 服务重启即全部失效（单机演示场景的预期行为）。
     */
    @PostMapping("/login")
    public ApiResponse<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request);
        return ApiResponse.ok(Map.of("token", token));
    }
}
