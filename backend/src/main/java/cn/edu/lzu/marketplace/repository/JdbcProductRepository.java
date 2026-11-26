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
 * 商品的 JDBC 数据访问实现。使用 JdbcTemplate 查询和更新 products 表，输入值通过占位符绑定。
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
 * <p>created_at 由业务层写入，表约束要求非空。列表按 id 倒序排序，组合筛选和分页在同一处组装。
 */
@Repository
public class JdbcProductRepository implements ProductRepository {

    // 详情和分页使用同一组查询列，保持读取字段一致。
    private static final String SELECT_COLUMNS =
            "id, title, price, stock, category, description, status, seller_name, created_at";

    // 将数据库中的一行记录转换为 Java Product 对象，供详情和分页共用。
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

    // 构造器接收 Spring 配置的 JdbcTemplate，复用数据源及事务连接，可用来执行 SQL 语句。
    private final JdbcTemplate jdbcTemplate;

    public JdbcProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 发布商品：按列顺序绑定字段，并取回数据库生成的商品 ID；INSERT，保存商品并获得自增 ID。 */
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

    /** 商品详情：按主键绑定查询条件，缺失结果由上层解释；SELECT，获取指定商品信息。 */
    @Override
    public Optional<Product> findById(Long id) {
        List<Product> rows = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM products WHERE id = ?", ROW_MAPPER, id);
        return rows.stream().findFirst();
    }

    /** 组合筛选与分页：根据关键词、状态和分类筛选商品，用相同的 WHERE 条件统计总数并查询当前页。 */
    @Override
    public PageResult<Product> findPage(long offset, int limit, String keyword, String status, String category) {
        // ① 动态筛选
        // 只拼接预定义的条件片段；用户输入通过参数绑定，避免参与 SQL 语法构造。
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

        // ② 统计符合全部筛选条件的记录数，沿用按条件出现顺序收集的参数。
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products" + where, Long.class, params.toArray());
        long totalCount = total == null ? 0L : total;

        // ③ 查询当前页：在筛选参数之后依次绑定 LIMIT（每页条数）和 OFFSET（起始偏移量）。
        // 两者都能传入合法数字，绑定顺序错误时 SQL 仍可能成功执行，但返回错误的页。
        params.add(limit);
        params.add(offset);
        List<Product> items = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM products" + where
                        + " ORDER BY id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, params.toArray());

        return new PageResult<>(totalCount, items);
    }

    /** 先转义反斜杠，再转义输入中的 % 和 _，使只有外层通配符参与子串匹配；转义关键词中的特殊通配符，保证按字面含义进行模糊查询。 */
    private static String escapeLike(String keyword) {
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** 状态更新：返回影响行数，供业务层识别不存在的商品。 */
    @Override
    public int updateStatus(Long id, String status) {
        return jdbcTemplate.update("UPDATE products SET status = ? WHERE id = ?", status, id);
    }

    /** 下单扣库存：通过单条条件 UPDATE 原子检查上架状态及剩余库存；通过原子条件 UPDATE 检查商品状态和库存并完成扣减，防止并发超卖。 */
    @Override
    public int deductStockIfAvailable(Long id, int quantity) {
        // 以执行更新时的库存为准，避免并发请求按同一份库存快照扣减。
        return jdbcTemplate.update(
                "UPDATE products SET stock = stock - ? WHERE id = ? AND status = ? AND stock >= ?",
                quantity, id, Product.STATUS_ON, quantity);
    }
}
