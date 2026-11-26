package com.example.demo.config;

import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TokenInterceptor implements HandlerInterceptor {
    private final AuthService authService;

    public TokenInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Check the mapped method, so protection uses the same mapping as the controller.
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(RequireToken.class)) {
            authService.requireAuthorization(request.getHeader("Authorization"));
        }
        return true;
    }
}
