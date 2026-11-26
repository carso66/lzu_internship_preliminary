package com.lzu.marketplace.auth;

import com.lzu.marketplace.common.BusinessException;
import com.lzu.marketplace.common.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录与 token 校验（官方 §6 L2-11）。
 *
 * <p><b>账号写死</b>：题目明确「账号写死」，不建用户表。演示账号
 * {@code admin / market123}，不含任何真实私人凭据（题目 §9 红线）。
 *
 * <p><b>token 存储</b>：单实例内存 {@link Set}。进程重启即全部失效——
 * 对本题的单机内存 H2 场景这是预期行为（题目不要求 JWT、不要求过期）。
 * 若将来要持久化或多实例，加一张 token 表或换 JWT 即可，接口形状不变。
 *
 * <p><b>失败文案统一</b>：账号不存在与密码错误返回同一句话、同一业务码，
 * 不让客户端通过报错差异探测「哪个账号存在」（防用户名枚举）。
 */
@Service
public class AuthService {

    /** 演示账号，题目要求写死（测试包同包可见，测试直接引用这一份常量）。 */
    static final String USERNAME = "admin";
    static final String PASSWORD = "market123";

    /** 已签发且未失效的 token。ConcurrentHashMap.newKeySet() 支持并发登录与校验。 */
    private final Set<String> validTokens = ConcurrentHashMap.newKeySet();

    /**
     * 校验账密并签发随机 token。
     *
     * @throws BusinessException 400 / 40001 账号或密码不正确
     */
    public String login(LoginRequest req) {
        boolean ok = USERNAME.equals(req.username()) && PASSWORD.equals(req.password());
        if (!ok) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "账号或密码不正确");
        }
        String token = TokenGenerator.next();
        validTokens.add(token);
        return token;
    }

    /** token 是否有效（已签发且未随重启丢失）。 */
    public boolean isValid(String token) {
        return token != null && validTokens.contains(token);
    }
}
