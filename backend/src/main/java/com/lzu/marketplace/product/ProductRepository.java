package com.lzu.marketplace.product;

import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.product.dto.CreateProductRequest;
import com.lzu.marketplace.product.dto.ProductQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * products 表的持久层（JdbcTemplate，无 ORM）。
 *
 * <p>所有 SQL 一律使用 {@code ?} 参数绑定，不做字符串拼接，避免 SQL 注入。
 */
@Repository
public class ProductRepository {

    private static final String COLUMNS =
            "id, title, price, stock, category, description, status, seller_name, created_at";

    /** 结果集 → 领域对象。snake_case 列名到 camelCase 字段的映射集中在这一处。 */
    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getLong("price"),
            rs.getInt("stock"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("seller_name"),
            rs.getObject("created_at", java.time.LocalDateTime.class));

    private final JdbcTemplate jdbc;

    public ProductRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 插入一条商品并回填数据库生成的 id 与 created_at。
     *
     * <p>{@code status} 由服务层决定（新商品固定 on），不从请求体接收。
     *
     * @return 含 id / created_at 的完整商品
     */
    public Product insert(CreateProductRequest req, String status) {
        String sql = "INSERT INTO products (title, price, stock, category, description, status, seller_name) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?)";

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            // 显式列出要回填的列，只取 id。
            // 若用 RETURN_GENERATED_KEYS，H2 会把带 DEFAULT 的 created_at 也一并返回，
            // KeyHolder 中就有多个键，getKey() 会抛 InvalidDataAccessApiUsageException。
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            ps.setString(1, req.title());
            ps.setLong(2, req.price());
            ps.setInt(3, req.stock());
            ps.setString(4, req.category());
            ps.setString(5, req.description());
            ps.setString(6, status);
            ps.setString(7, req.sellerName());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入商品后未取得自增主键");
        }
        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("插入商品后无法回读记录 id=" + key));
    }

    public Optional<Product> findById(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM products WHERE id = ?", ROW_MAPPER, id)
                .stream()
                .findFirst();
    }

    /**
     * 更新商品上下架状态。
     *
     * <p>返回受影响行数：0 表示该 id 不存在（状态相同也会返回 1，
     * 因为 UPDATE 命中行即计数，不受值是否变化影响）。
     *
     * <p>调用方需要先判断商品是否存在再调用，或根据返回值区分「不存在」与「更新成功」。
     */
    public int updateStatus(long id, String status) {
        return jdbc.update("UPDATE products SET status = ? WHERE id = ?", status, id);
    }

    /**
     * 按筛选条件分页查询，同时返回筛选后的总数。
     *
     * <p><b>COUNT 与数据查询共用同一份 WHERE 子句与同一个参数列表</b>（见 {@link #buildFilter}），
     * 保证 total 与 items 永远基于同一组过滤条件，不会出现「total 说 5 条、翻页只有 3 条」。
     */
    public PageResult<Product> search(ProductQuery q) {
        Filter filter = buildFilter(q);

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products" + filter.whereClause(),
                Long.class,
                filter.args().toArray());

        List<Product> items = jdbc.query(
                "SELECT " + COLUMNS + " FROM products" + filter.whereClause()
                        // 确定性排序：created_at 可能并列（同一毫秒批量插入），id 提供稳定的次序，
                        // 否则翻页时同一条记录可能重复出现或漏掉。
                        + " ORDER BY created_at DESC, id DESC"
                        + " LIMIT ? OFFSET ?",
                ROW_MAPPER,
                appendArgs(filter.args(), q.pageSize(), q.offset()).toArray());

        return PageResult.of(total == null ? 0L : total, q.page(), q.pageSize(), items);
    }

    /**
     * 构造 WHERE 子句与对应参数。
     *
     * <p>只用 {@code ?} 占位符拼装结构，值一律走参数绑定：
     * <ul>
     *   <li>{@code keyword} 是唯一需要处理通配符的：用户输入的 {@code %} 和 {@code _}
     *       会被转义，否则输入 {@code %} 会匹配所有商品；</li>
     *   <li>ESCAPE 字符选 {@code !}（反斜杠在部分数据库/驱动里需要双重转义，容易出错）。</li>
     * </ul>
     */
    private Filter buildFilter(ProductQuery q) {
        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();

        if (q.keyword() != null) {
            where.append(where.isEmpty() ? " WHERE " : " AND ");
            where.append("LOWER(title) LIKE LOWER(?) ESCAPE '!'");
            args.add("%" + escapeLike(q.keyword()) + "%");
        }
        if (q.status() != null) {
            where.append(where.isEmpty() ? " WHERE " : " AND ");
            where.append("status = ?");
            args.add(q.status());
        }
        if (q.category() != null) {
            where.append(where.isEmpty() ? " WHERE " : " AND ");
            where.append("category = ?");
            args.add(q.category());
        }
        return new Filter(where.toString(), args);
    }

    /** 转义 LIKE 的通配符，使它们按字面量匹配。 */
    private static String escapeLike(String raw) {
        return raw.replace("!", "!!")
                  .replace("%", "!%")
                  .replace("_", "!_");
    }

    private static List<Object> appendArgs(List<Object> base, Object... extra) {
        List<Object> all = new ArrayList<>(base);
        all.addAll(Arrays.asList(extra));
        return all;
    }

    /** WHERE 子句 + 参数，供 COUNT 与数据查询共用。 */
    private record Filter(String whereClause, List<Object> args) {
    }
}
