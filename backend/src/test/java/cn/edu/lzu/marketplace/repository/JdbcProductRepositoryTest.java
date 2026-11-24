package cn.edu.lzu.marketplace.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockingDetails;

/**
 * Repository 层单元测试：Mock JdbcTemplate，<b>直接断言最终传给 JDBC 的 SQL 文本
 * 和占位符参数顺序</b>，而不是只验证 Service→Repository 的调用。
 *
 * <p>通过 {@code mockingDetails(...).getInvocations()} 读取真实记录的调用参数
 * （varargs 已展开），避免 Mockito 在变长参数捕获上的匹配歧义，
 * 能可靠地抓出「SQL 占位符顺序 vs 绑定顺序不一致」这类错误。
 */
@ExtendWith(MockitoExtension.class)
class JdbcProductRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcProductRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcProductRepository(jdbcTemplate);
    }

    /** 找到 SQL 文本包含指定片段的调用（query/findById 用 query，update 用 update） */
    private Invocation invocationWithSql(String marker) {
        for (Invocation inv : mockingDetails(jdbcTemplate).getInvocations()) {
            Object[] args = inv.getArguments();
            if (args.length > 0 && args[0] instanceof String && ((String) args[0]).contains(marker)) {
                return inv;
            }
        }
        throw new AssertionError("未找到 SQL 包含 [" + marker + "] 的 JdbcTemplate 调用");
    }

    /** 取占位符实参列表：跳过前导的 sql / rowMapper，返回其后的绑定参数（varargs 展开） */
    private static List<Object> boundArgs(Invocation inv, int fixedLeading) {
        Object[] all = inv.getArguments();
        List<Object> out = new ArrayList<>();
        for (int i = fixedLeading; i < all.length; i++) {
            // 兼容 Mockito 将整段 varargs 作为单个 Object[] 记录的情况
            if (all.length - fixedLeading == 1 && all[i] instanceof Object[]) {
                out.addAll(Arrays.asList((Object[]) all[i]));
            } else {
                out.add(all[i]);
            }
        }
        return out;
    }

    @Test
    void findPageBindsLimitBeforeOffset() {
        // 第 1 页：offset=0, limit=10 —— 修复前会错误地绑定成 LIMIT 0 OFFSET 10
        repository.findPage(0L, 10, null, null, null);

        Invocation inv = invocationWithSql("LIMIT ? OFFSET ?");
        String sql = (String) inv.getArguments()[0];
        assertTrue(sql.endsWith("ORDER BY id DESC LIMIT ? OFFSET ?"), sql);

        List<Object> bound = boundArgs(inv, 2); // 跳过 sql + rowMapper
        assertEquals(10, bound.get(0), "LIMIT 占位符必须先绑定每页条数");
        assertEquals(0L, bound.get(1), "OFFSET 占位符必须后绑定偏移量");
    }

    @Test
    void findPageBindsLimitBeforeOffsetWhenOffsetLargerThanLimit() {
        // offset(20) > limit(10)：顺序写反会被本用例捕获成 [20,10]
        repository.findPage(20L, 10, null, null, null);

        List<Object> bound = boundArgs(invocationWithSql("LIMIT ? OFFSET ?"), 2);
        assertEquals(10, bound.get(0));
        assertEquals(20L, bound.get(1));
    }

    @Test
    void findPageEscapesLikeWildcardsInKeyword() {
        repository.findPage(0L, 10, "100%_off", null, null);

        // 用仅分页查询才有的 LIMIT 标记定位（COUNT 查询也含 LIKE，会被误匹配）
        Invocation inv = invocationWithSql("LIMIT ? OFFSET ?");
        assertTrue(((String) inv.getArguments()[0]).contains("title LIKE ? ESCAPE"),
                "keyword 查询必须带 ESCAPE 子句");

        List<Object> bound = boundArgs(inv, 2);
        // 用户输入的 % 和 _ 被转义为字面量，只有两端的首尾 % 是通配符
        assertEquals("%100\\%\\_off%", bound.get(0));
        assertEquals(10, bound.get(1));
        assertEquals(0L, bound.get(2));
    }

    @Test
    void findPageCombinesFiltersInPlaceholderOrder() {
        repository.findPage(20L, 10, "书", "on", "文具");

        List<Object> bound = boundArgs(invocationWithSql("LIMIT ? OFFSET ?"), 2);
        // 绑定顺序与占位符出现顺序一致：keyword, status, category, LIMIT, OFFSET
        assertEquals(List.of("%书%", "on", "文具", 10, 20L), bound);
    }

    @Test
    void findByIdBindsIdAsSingleParam() {
        repository.findById(7L);

        List<Object> bound = boundArgs(invocationWithSql("FROM products WHERE id = ?"), 2);
        assertEquals(List.of(7L), bound);
    }

    @Test
    void updateStatusBindsStatusBeforeId() {
        repository.updateStatus(5L, "off");

        Invocation inv = invocationWithSql("UPDATE products SET status = ?");
        assertEquals("UPDATE products SET status = ? WHERE id = ?", inv.getArguments()[0]);
        List<Object> bound = boundArgs(inv, 1); // update 只有 sql 是前导
        assertEquals(List.of("off", 5L), bound);
    }
}
