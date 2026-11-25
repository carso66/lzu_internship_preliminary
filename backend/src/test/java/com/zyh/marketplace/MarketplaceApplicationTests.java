package com.zyh.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 上下文加载测试。
 * 使用 test 配置：内存库 + 与正式库相同的 schema.sql，不会触碰本地文件库。
 */
@SpringBootTest
@ActiveProfiles("test")
class MarketplaceApplicationTests {

	@Test
	void contextLoads() {
	}

}
