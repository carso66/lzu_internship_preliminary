package com.lzu.marketplace.schema;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * L2-13 索引专项：L1-8 订单查询加索引前后的可复核对比。
 *
 * <p>被测查询（与 {@code OrderRepository.searchByBuyerPhone} 的数据查询同构）：
 * <pre>SELECT ... FROM orders WHERE buyer_phone = ? ORDER BY created_at DESC, id DESC LIMIT 10 OFFSET 0</pre>
 *
 * <p>对比方法：同一份数据、同一条 SQL ——
 * <ol>
 *   <li>{@code DROP INDEX} 后 {@code EXPLAIN ANALYZE}（无索引基线）；</li>
 *   <li>按 schema.sql 的原定义重建索引后再 {@code EXPLAIN ANALYZE}。</li>
 * </ol>
 * 两份执行计划与 scanCount（H2 记录的物理扫描行数）打印到测试输出，
 * 结论引用在 DESIGN.md 附录 A。评审重跑 {@code mvn test -Dtest=IndexExplainTest}
 * 即可复核——数据由本类自动构建，不依赖手工准备。
 *
 * <p><b>实测结论（H2 2.x）</b>：索引把扫描行数从全表 20001 收敛到该买家 1001
 * （约 20 倍），但 H2 不沿索引序在 LIMIT 处提前终止——免排序需要引擎支持
 * backward index scan，H2 在本查询形态下不做；该结论由 scanCount 本身证明，
 * 与 EXPLAIN 文本是否回显 ORDER BY 无关。
 *
 * <p>数据分布刻意贴近业务：20 个买家各 1000 单（共 2 万行），
 * created_at 逐行递增（模拟「按时间累积」而非全部挤在同一毫秒）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IndexExplainTest {

    /** 固定查询参数（典型值：老买家、看第一页）。必须是种子数据里真实存在的手机号。 */
    private static final String PHONE = "13800000000";
    private static final String QUERY =
            "SELECT id, order_no, product_id, product_title, quantity, unit_price, total_amount, " +
            "buyer_name, buyer_phone, address, status, created_at, paid_at " +
            "FROM orders WHERE buyer_phone = ? " +
            "ORDER BY created_at DESC, id DESC LIMIT 10 OFFSET 0";

    private static final int BUYERS = 20;
    private static final int ORDERS_PER_BUYER = 1000;
    private static final int TOTAL = BUYERS * ORDERS_PER_BUYER;

    /** H2 把不带引号的标识符存为大写，schema.sql 里的索引即这个名字。 */
    private static final String INDEX_NAME = "IDX_ORDERS_BUYER_PHONE_CREATED";
    private static final String RECREATE_INDEX_DDL = "CREATE INDEX " + INDEX_NAME +
            " ON orders (buyer_phone, created_at DESC, id DESC)";

    @Autowired
    private JdbcTemplate jdbc;

    /** phase1 记录的无索引 scanCount，供 phase2 做相对比较。 */
    private int noIndexScanCount = -1;

    @BeforeAll
    void seed() {
        jdbc.update("DELETE FROM orders");
        jdbc.update("DELETE FROM products");
        jdbc.update("INSERT INTO products (title, price, stock, category, status, seller_name) " +
                    "VALUES ('索引测试商品', 100, " + TOTAL + ", '测试', 'on', '店铺')");
        Long productId = jdbc.queryForObject("SELECT MAX(id) FROM products", Long.class);

        LocalDateTime base = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        List<Object[]> batch = new ArrayList<>();
        long seq = 0;
        for (int buyer = 0; buyer < BUYERS; buyer++) {
            String phone = "138" + String.format("%08d", buyer);
            for (int i = 0; i < ORDERS_PER_BUYER; i++) {
                seq++;
                batch.add(new Object[]{
                        "IX" + String.format("%010d", seq), // order_no，全局唯一
                        productId, "索引测试商品", 1, 100, 100,
                        "买家" + buyer, phone, "兰州市城关区",
                        Timestamp.valueOf(base.plusSeconds(seq))});
                if (batch.size() >= 500) {
                    insertBatch(batch);
                }
            }
        }
        insertBatch(batch);
    }

    private void insertBatch(List<Object[]> batch) {
        if (batch.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                "INSERT INTO orders (order_no, product_id, product_title, quantity, unit_price, " +
                "total_amount, buyer_name, buyer_phone, address, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batch);
        batch.clear();
    }

    /** 无论测试结果如何，结束时把索引恢复成 schema.sql 的原样（共享同一 Spring 上下文）。 */
    @AfterAll
    void restoreIndex() {
        jdbc.update("DROP INDEX IF EXISTS " + INDEX_NAME);
        jdbc.update(RECREATE_INDEX_DDL);
    }

    @Test
    @Order(1)
    void phase1_noIndex_baseline() {
        jdbc.update("DROP INDEX IF EXISTS " + INDEX_NAME);

        Result r = analyze();
        noIndexScanCount = r.scanCount;
        System.out.println("[L2-13] 无索引 EXPLAIN: " + r.plan);
        System.out.println("[L2-13] 无索引 scanCount = " + r.scanCount + "（表总数 " + TOTAL + "）");

        org.junit.jupiter.api.Assertions.assertTrue(r.parseOk,
                "应能从 EXPLAIN ANALYZE 解析出 scanCount，实际输出: " + r.plan);
        org.junit.jupiter.api.Assertions.assertTrue(r.plan.contains("tableScan"),
                "无索引时应是全表扫描，实际计划: " + r.plan);
        org.junit.jupiter.api.Assertions.assertTrue(r.scanCount >= TOTAL,
                "无索引时排序取前 10 条前必须读入全表 " + TOTAL + " 行，实际 scanCount=" + r.scanCount);
    }

    @Test
    @Order(2)
    void phase2_withIndex() {
        jdbc.update("DROP INDEX IF EXISTS " + INDEX_NAME);
        jdbc.update(RECREATE_INDEX_DDL);

        Result r = analyze();
        System.out.println("[L2-13] 有索引 EXPLAIN: " + r.plan);
        System.out.println("[L2-13] 有索引 scanCount = " + r.scanCount
                + "（对比无索引 " + noIndexScanCount + "）");

        org.junit.jupiter.api.Assertions.assertTrue(r.parseOk,
                "应能从 EXPLAIN ANALYZE 解析出 scanCount，实际输出: " + r.plan);
        org.junit.jupiter.api.Assertions.assertTrue(r.plan.contains(INDEX_NAME),
                "应走索引范围扫描（计划含 " + INDEX_NAME + "），实际: " + r.plan);

        // 实测语义（H2 2.x，20 买家 × 1000 单）：
        //   索引把扫描范围从全表收敛到「该买家的 1000 行」（scanCount 20001 → 1001）；
        //   但 H2 不沿索引序在 LIMIT 10 处提前终止（若免排序成立应 ≈ 11 行），
        //   即本库用索引做范围收敛、不做免排序优化 —— 与量级断言严格一致。
        org.junit.jupiter.api.Assertions.assertTrue(r.scanCount <= ORDERS_PER_BUYER * 2,
                "有索引时扫描应收敛到该买家范围（约 " + ORDERS_PER_BUYER + " 行，留 2 倍余量），实际 scanCount=" + r.scanCount);
        org.junit.jupiter.api.Assertions.assertTrue(r.scanCount >= 10,
                "要返回 10 行至少要扫 10 行，实际 scanCount=" + r.scanCount);
        org.junit.jupiter.api.Assertions.assertTrue(noIndexScanCount <= 0 || r.scanCount * 10 < noIndexScanCount,
                "有索引的扫描量应比无索引低一个数量级以上: 有=" + r.scanCount
                        + ", 无=" + noIndexScanCount);
    }

    /** EXPLAIN ANALYZE 目标查询，解析执行计划全文与 scanCount。 */
    private Result analyze() {
        List<Map<String, Object>> rows = jdbc.queryForList("EXPLAIN ANALYZE " + QUERY, PHONE);
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : rows) {
            for (Object v : row.values()) {
                sb.append(v).append('\n');
            }
        }
        String plan = sb.toString().trim();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("scanCount:\\s*(\\d+)")
                .matcher(plan);
        boolean parseOk = m.find();
        int scanCount = parseOk ? Integer.parseInt(m.group(1)) : -1;
        return new Result(plan, scanCount, parseOk);
    }

    private record Result(String plan, int scanCount, boolean parseOk) {
    }
}
