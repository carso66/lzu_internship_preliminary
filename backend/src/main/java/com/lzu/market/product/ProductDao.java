package com.lzu.market.product;

import com.lzu.market.common.ApiException;
import com.lzu.market.common.PageResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ProductDao {

    private static final String COLS =
            "id, title, price, stock, category, description, status, seller_name, created_at";

    private static final RowMapper<Product> MAPPER = (ResultSet rs, int rowNum) -> new Product(
            rs.getLong("id"),
            rs.getString("title"),
            rs.getInt("price"),
            rs.getInt("stock"),
            rs.getString("category"),
            rs.getString("description"),
            rs.getString("status"),
            rs.getString("seller_name"),
            rs.getTimestamp("created_at").toLocalDateTime());

    private final JdbcTemplate jdbc;

    public ProductDao(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 发布商品，返回带自增 id 的完整对象 */
    public Product create(CreateProductReq req) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO products (title, price, stock, category, description, status, seller_name) "
                            + "VALUES (?, ?, ?, ?, ?, 'on', ?)",
                    new String[]{"id"});
            ps.setString(1, req.title().trim());
            ps.setInt(2, req.price());
            ps.setInt(3, req.stock());
            ps.setString(4, req.category().trim());
            ps.setString(5, req.description());
            ps.setString(6, req.sellerName().trim());
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        return findById(id).orElseThrow(() -> new ApiException(500, 50000, "商品写入后读不到"));
    }

    public Optional<Product> findById(long id) {
        List<Product> list = jdbc.query(
                "SELECT " + COLS + " FROM products WHERE id = ?", MAPPER, id);
        return list.stream().findFirst();
    }

    /** 三个筛选项可叠加，total 与 items 共用同一个 where 与 args */
    public PageResult<Product> findPage(String keyword, String status, String category,
                                        int page, int pageSize) {
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND title LIKE ?");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            args.add(status);
        }
        if (category != null && !category.isBlank()) {
            where.append(" AND category = ?");
            args.add(category.trim());
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM products" + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(pageSize);
        pageArgs.add((long) (page - 1) * pageSize);

        List<Product> items = jdbc.query(
                "SELECT " + COLS + " FROM products" + where
                        + " ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?",
                MAPPER, pageArgs.toArray());

        return new PageResult<>(total == null ? 0 : total, page, pageSize, items);
    }

    /** 返回受影响行数，0 表示商品不存在 */
    public int updateStatus(long id, String status) {
        return jdbc.update("UPDATE products SET status = ? WHERE id = ?", status, id);
    }

    /** 条件减库存：affectedRows 是 0 就代表库存不够，不靠先查后改 */
    public int deductStock(long productId, int quantity) {
        return jdbc.update(
                "UPDATE products SET stock = stock - ? WHERE id = ? AND stock >= ?",
                quantity, productId, quantity);
    }
}
