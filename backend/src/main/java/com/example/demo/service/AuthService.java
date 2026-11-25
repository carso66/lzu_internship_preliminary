package com.example.demo.service;

import com.example.demo.common.BizException;
import com.example.demo.common.ErrorCode;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.LoginResponse;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {
    // Fixed demonstration account required by L2-11.
    private static final String USERNAME = "demo";
    private static final String PASSWORD = "demo123";
    private final SecureRandom random = new SecureRandom();
    private final Set<String> tokens = ConcurrentHashMap.newKeySet();

    public LoginResponse login(LoginRequest request) {
        if (request == null || request.username() == null || request.username().isBlank()
                || request.password() == null || request.password().isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "username 和 password 不能为空");
        }
        if (!USERNAME.equals(request.username()) || !PASSWORD.equals(request.password())) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "账号或密码错误");
        }
        String token;
        do {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (!tokens.add(token));
        return new LoginResponse(token);
    }

    public void requireAuthorization(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)
                || !tokens.contains(authorization.substring(7))) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少或无效的 Bearer token");
        }
    }
}
