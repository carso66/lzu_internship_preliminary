package cn.edu.lzu.marketplace;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** 启动冒烟测试，确认应用上下文及 H2 数据源可以加载。 */
@SpringBootTest
class MarketplaceApplicationTests {

    @Test
    void contextLoads() {
        // 上下文在进入此方法前完成初始化，启动失败会使测试失败。
    }
}
