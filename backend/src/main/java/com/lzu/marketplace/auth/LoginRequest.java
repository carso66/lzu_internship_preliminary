package com.lzu.marketplace.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登录请求体（官方 §6 L2-11：账号密码写死，不接数据库）。
 *
 * <p>不做额外格式校验：登录失败统一由 {@link AuthService#login} 返回
 * 「账号或密码不正确」，不让客户端通过报错文案探测「哪个账号存在」
 * （用户名枚举防御）。字段长度上限只是防御性收口，正常输入碰不到。
 *
 * @param username 账号
 * @param password 密码
 */
public record LoginRequest(

        @NotBlank(message = "不能为空")
        @Size(max = 64, message = "不能超过 64 字符")
        String username,

        @NotBlank(message = "不能为空")
        @Size(max = 64, message = "不能超过 64 字符")
        String password) {
}
