package com.lzu.marketplace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 校园好物市集 · 后端应用入口。
 *
 * <p>模块化单体：按业务模块分包（product / order / payment），
 * 公共能力放在 common 包中，避免跨模块直接依赖实现细节。
 */
@SpringBootApplication
public class MarketplaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(MarketplaceApplication.class, args);
    }
}
