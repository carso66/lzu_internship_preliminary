package com.lzu.marketplace.product.repository;

import com.lzu.marketplace.common.PageResult;
import com.lzu.marketplace.product.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 商品数据访问：只负责 SQL 和行映射，所有查询条件都用参数绑定，不拼接用户输入。
 */
@Repository
public class ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private static final String SELECT_SQL =
            "SELECT id, title, price, stock, category, description, status, seller_name, created_at FROM products";

    private static final String INSERT_SQL =
            "INSERT INTO products (title, price, stock, category, description, seller_name) VALUES (?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_STATUS_SQL =
            "UPDATE products SET status = ? WHERE id = ?";

    /**
     * 原子扣减库存：带 status='on' 和 stock>=? 条件，由数据库在拿到行锁后按最新数据判定。
     *
     * <p>受影响行数为 1 表示扣减成功；为 0 表示没扣成（商品不存在、已下架或库存不够），
     * 到底是哪种原因由调用方再查一次商品来区分。</p>
     */
    private static final String DEDUCT_STOCK_SQL =
            "UPDATE products SET stock = stock - ? WHERE id = ? AND status = 'on' AND stock >= ?";

    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getLong("price"),
            rs.getInt("stock"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("seller_name"),
            rs.getTimestamp("created_at").toLocalDateTime()
    );

    public Product findById(long id) {
        List<Product> list = jdbcTemplate.query(SELECT_SQL + " WHERE id = ?", ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** 插入并回读完整行（拿到自增 id、默认状态和 created_at）。 */
    public Product insert(String title, long price, int stock, String category, String description, String sellerName) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            // 只返回 ID 一列，避免 H2 同时返回 CREATED_AT 导致 getKey() 抛异常
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, new String[]{"ID"});
            ps.setString(1, title);
            ps.setLong(2, price);
            ps.setInt(3, stock);
            ps.setString(4, category);
            ps.setString(5, description);
            ps.setString(6, sellerName);
            return ps;
        }, keyHolder);
        long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return findById(id);
    }

    /** 返回受影响行数，0 表示商品不存在。 */
    public int updateStatus(long id, String status) {
        return jdbcTemplate.update(UPDATE_STATUS_SQL, status, id);
    }

    /**
     * 扣减库存，返回受影响行数（1 = 扣减成功，0 = 没扣成）。
     *
     * <p>把"商品在架"和"库存够"都写进 WHERE 条件，数据库会在加锁后按最新值重新判定，
     * 因此并发下单不会把库存扣成负数（不会超卖）。</p>
     */
    public int deductStock(long productId, int quantity) {
        return jdbcTemplate.update(DEDUCT_STOCK_SQL, quantity, productId, quantity);
    }

    /**
     * 分页查询：keyword 模糊、status 精确、category 精确，三者 AND 叠加；
     * 排序固定为 id 倒序，保证分页顺序稳定；offset 用 long 避免 int 溢出。
     */
    public PageResult<Product> findPage(String keyword, String status, String category,
                                        int page, int pageSize, long offset) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();

        if (keyword != null) {
            conditions.add("title LIKE ?");
            params.add("%" + keyword + "%");
        }
        if (status != null) {
            conditions.add("status = ?");
            params.add(status);
        }
        if (category != null) {
            conditions.add("category = ?");
            params.add(category);
        }

        String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);

        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM products" + where, Long.class, params.toArray());

        List<Object> pageParams = new ArrayList<>(params);
        pageParams.add(pageSize);
        pageParams.add(offset);
        List<Product> items = jdbcTemplate.query(
                SELECT_SQL + where + " ORDER BY id DESC LIMIT ? OFFSET ?",
                ROW_MAPPER,
                pageParams.toArray());

        return new PageResult<>(total, page, pageSize, items);
    }
}
