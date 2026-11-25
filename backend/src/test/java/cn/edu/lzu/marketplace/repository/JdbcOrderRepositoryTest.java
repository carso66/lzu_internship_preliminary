package cn.edu.lzu.marketplace.repository;

import cn.edu.lzu.marketplace.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模拟 JdbcTemplate，检查订单查询、插入和编号回填的 SQL 与占位符实参。
 *
 * <p>通过 {@code mockingDetails(...).getInvocations()} 读取真实记录的调用参数
 * （varargs 已展开），避免 Mockito 在变长参数捕获上的匹配歧义。
 */
@ExtendWith(MockitoExtension.class)
class JdbcOrderRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private JdbcOrderRepository repository;

    @BeforeEach
    void setUp() {
        repository = new JdbcOrderRepository(jdbcTemplate);
    }

    /** 找到 SQL 文本包含指定片段的调用（query 用 query，update 用 update） */
    private static org.mockito.invocation.Invocation invocationWithSql(JdbcTemplate jdbcTemplate, String marker) {
        for (org.mockito.invocation.Invocation inv : mockingDetails(jdbcTemplate).getInvocations()) {
            Object[] args = inv.getArguments();
            if (args.length > 0 && args[0] instanceof String && ((String) args[0]).contains(marker)) {
                return inv;
            }
        }
        throw new AssertionError("未找到 SQL 包含 [" + marker + "] 的 JdbcTemplate 调用");
    }

    private org.mockito.invocation.Invocation invocationWithSql(String marker) {
        return invocationWithSql(jdbcTemplate, marker);
    }

    /** 取占位符实参列表：跳过前导的 sql / rowMapper，返回其后的绑定参数（varargs 展开） */
    private static List<Object> boundArgs(org.mockito.invocation.Invocation inv, int fixedLeading) {
        Object[] all = inv.getArguments();
        List<Object> out = new ArrayList<>();
        for (int i = fixedLeading; i < all.length; i++) {
            if (all.length - fixedLeading == 1 && all[i] instanceof Object[]) {
                out.addAll(Arrays.asList((Object[]) all[i]));
            } else {
                out.add(all[i]);
            }
        }
        return out;
    }

    // ① 订单查询：核对手机号、分页参数及详情查询的绑定，并检查稳定排序。
    @Test
    void findPageBindsBuyerPhoneThenLimitThenOffset() {
        repository.findPage(20L, 10, "13800138000");

        org.mockito.invocation.Invocation inv = invocationWithSql("LIMIT ? OFFSET ?");
        String sql = (String) inv.getArguments()[0];
        assertTrue(sql.endsWith("ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?"), sql);

        List<Object> bound = boundArgs(inv, 2);
        // 绑定顺序与占位符出现顺序一致：buyer_phone, LIMIT(每页条数), OFFSET(偏移量)
        assertEquals(List.of("13800138000", 10, 20L), bound);
    }

    @Test
    void findPageOrdersByCreatedAtDescWithIdTiebreaker() {
        repository.findPage(0L, 10, "13800138000");

        String sql = (String) invocationWithSql("ORDER BY created_at DESC, id DESC").getArguments()[0];
        assertTrue(sql.contains("ORDER BY created_at DESC, id DESC"), sql);
    }

    @Test
    void findByOrderNoBindsSingleParam() {
        repository.findByOrderNo("SO202611190001");

        List<Object> bound = boundArgs(invocationWithSql("FROM orders WHERE order_no = ?"), 2);
        assertEquals(List.of("SO202611190001"), bound);
    }

    // ② 订单写入：核对编号回填的参数和插入语句的列顺序。
    @Test
    void updateOrderNoBindsOrderNoBeforeId() {
        repository.updateOrderNo(5L, "SO2026111900005");

        org.mockito.invocation.Invocation inv = invocationWithSql("UPDATE orders SET order_no = ?");
        assertEquals("UPDATE orders SET order_no = ? WHERE id = ?", inv.getArguments()[0]);
        List<Object> bound = boundArgs(inv, 1);
        assertEquals(List.of("SO2026111900005", 5L), bound);
    }

    @Test
    void insertBindsParamsInColumnOrder() throws SQLException {
        Order order = new Order(null, "P123", 3L, "考研数学复习全书", 2, 4990L, 9980L,
                "张三", "13800138000", "地址", "created", LocalDateTime.of(2026, 11, 24, 12, 0), null);

        // insert 走 PreparedStatementCreator 重载；mock 的 update 不填充 KeyHolder，getKey() 为 null → 抛异常
        ArgumentCaptor<PreparedStatementCreator> creatorCap =
                ArgumentCaptor.forClass(PreparedStatementCreator.class);
        assertThrows(IllegalStateException.class, () -> repository.insert(order));
        verify(jdbcTemplate).update(creatorCap.capture(), any(KeyHolder.class));

        PreparedStatement ps = mock(PreparedStatement.class);
        Connection con = mock(Connection.class);
        when(con.prepareStatement(anyString(), eq(Statement.RETURN_GENERATED_KEYS))).thenReturn(ps);

        creatorCap.getValue().createPreparedStatement(con);

        InOrder inOrder = inOrder(ps);
        inOrder.verify(ps).setString(1, "P123");
        inOrder.verify(ps).setLong(2, 3L);
        inOrder.verify(ps).setString(3, "考研数学复习全书");
        inOrder.verify(ps).setInt(4, 2);
        inOrder.verify(ps).setLong(5, 4990L);
        inOrder.verify(ps).setLong(6, 9980L);
        inOrder.verify(ps).setString(7, "张三");
        inOrder.verify(ps).setString(8, "13800138000");
        inOrder.verify(ps).setString(9, "地址");
        inOrder.verify(ps).setString(10, "created");
        inOrder.verify(ps).setTimestamp(11, Timestamp.valueOf(LocalDateTime.of(2026, 11, 24, 12, 0)));
        inOrder.verify(ps).setTimestamp(12, (Timestamp) null);
    }
}
