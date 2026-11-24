package cn.edu.lzu.marketplace.repository;

import cn.edu.lzu.marketplace.model.PageResult;
import cn.edu.lzu.marketplace.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 商品的 JdbcTemplate 实现，全部使用参数化 SQL（? 占位符），杜绝 SQL 注入。
 *
 * <h3>数据库联调依据 —— 依赖的表结构与字段（与 schema.sql 对齐）</h3>
 * <pre>
 * 表名: products
 * | 列名         | 类型         | 约束/说明                    |
 * |--------------|-------------|-----------------------------|
 * | id           | BIGINT      | 主键, 自增                   |
 * | title        | VARCHAR(100)| 标题, 非空                   |
 * | price        | BIGINT      | 价格, 单位分, 整数, 非空      |
 * | stock        | INTEGER     | 库存数量, 非空               |
 * | category     | VARCHAR(50) | 分类, 非空                   |
 * | description  | VARCHAR(500)| 备注, 可空                   |
 * | status       | VARCHAR(10) | on/off, 非空                 |
 * | seller_name  | VARCHAR(50) | 卖家名称, 非空               |
 * | created_at   | TIMESTAMP   | 创建时间, 由应用写入         |
 * </pre>
 *
 * <p>说明：created_at 由 Service 层写入（LocalDateTime.now()），schema.sql 可设
 * NOT NULL；排序默认 id 倒序（README L0-3 未规定排序，此为自定选择）。
 * LIMIT/OFFSET 语法、以及 keyword 子串匹配用到的 {@code LIKE ... ESCAPE '\'}
 * 在 H2 的兼容性需待 schema.sql 就绪后实测确认。
 *
 * <h3>分页参数顺序（易错点）</h3>
 * <p>SQL 写的是 {@code LIMIT ? OFFSET ?}，JDBC 占位符按出现顺序绑定：第 1 个 ? 是
 * 每页条数(limit)，第 2 个 ? 是偏移量(offset)。绑定顺序必须 limit 在前、offset 在后。
 */
@Repository
public class JdbcProductRepository implements ProductRepository {

    private static final String SELECT_COLUMNS =
            "id, title, price, stock, category, description, status, seller_name, created_at";

    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getLong("price"),
            rs.getInt("stock"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("seller_name"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbcTemplate;

    public JdbcProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Long insert(Product product) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO products (title, price, stock, category, description, status, seller_name, created_at) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, product.title());
            ps.setLong(2, product.price());
            ps.setInt(3, product.stock());
            ps.setString(4, product.category());
            ps.setString(5, product.description());
            ps.setString(6, product.status());
            ps.setString(7, product.sellerName());
            ps.setTimestamp(8, Timestamp.valueOf(product.createdAt()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入商品后未返回自增主键");
        }
        return key.longValue();
    }

    @Override
    public Optional<Product> findById(Long id) {
        List<Product> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM products WHERE id = ?", ROW_MAPPER, id);
        return rows.stream().findFirst();
    }

    @Override
    public PageResult<Product> findPage(long offset, int limit, String keyword, String status, String category) {
        StringBuilder conditions = new StringBuilder();
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            // 子串匹配：用户输入的 % _ \ 必须转义为字面量，否则会被当成 LIKE 通配符
            conditions.append(" AND title LIKE ? ESCAPE '\\'");
            params.add("%" + escapeLike(keyword) + "%");
        }
        if (status != null && !status.isBlank()) {
            conditions.append(" AND status = ?");
            params.add(status);
        }
        if (category != null && !category.isBlank()) {
            conditions.append(" AND category = ?");
            params.add(category);
        }

        String where = conditions.isEmpty() ? "" : " WHERE 1=1" + conditions;

        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products" + where, Long.class, params.toArray());
        long totalCount = total == null ? 0L : total;

        // 占位符顺序是 LIMIT ? OFFSET ?，因此必须先绑定 limit（每页条数）再绑定 offset（偏移量）
        params.add(limit);
        params.add(offset);
        List<Product> items = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM products" + where
                        + " ORDER BY id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, params.toArray());

        return new PageResult<>(totalCount, items);
    }

    /** 转义 LIKE 通配符，保证 keyword 按字面子串匹配；先转义转义符本身 */
    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    @Override
    public int updateStatus(Long id, String status) {
        return jdbcTemplate.update("UPDATE products SET status = ? WHERE id = ?", status, id);
    }
}
