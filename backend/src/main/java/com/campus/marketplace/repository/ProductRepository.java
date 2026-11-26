package com.campus.marketplace.repository;

import com.campus.marketplace.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ProductRepository {

    /** 不插 created_at，交给数据库默认值 */
    private static final String INSERT_SQL =
            "INSERT INTO products (title, price, stock, category, description, status, seller_name) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_BY_ID_SQL =
            "SELECT id, title, price, stock, category, description, status, seller_name, created_at "
                    + "FROM products WHERE id = ?";

    private static final RowMapper<Product> ROW_MAPPER = (rs, rowNum) -> {
        Product p = new Product();
        p.setId(rs.getLong("id"));
        p.setTitle(rs.getString("title"));
        p.setPrice(rs.getInt("price"));
        p.setStock(rs.getInt("stock"));
        p.setCategory(rs.getString("category"));
        p.setDescription(rs.getString("description"));
        p.setStatus(rs.getString("status"));
        p.setSellerName(rs.getString("seller_name"));
        Timestamp ts = rs.getTimestamp("created_at");
        p.setCreatedAt(ts == null ? null : ts.toLocalDateTime());
        return p;
    };

    private final JdbcTemplate jdbcTemplate;

    public ProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 插入商品，返回自增主键 */
    public Long insert(Product product) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(INSERT_SQL, new String[]{"id"});
            ps.setString(1, product.getTitle());
            ps.setInt(2, product.getPrice());
            ps.setInt(3, product.getStock());
            ps.setString(4, product.getCategory());
            ps.setString(5, product.getDescription());
            ps.setString(6, product.getStatus());
            ps.setString(7, product.getSellerName());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入商品后未取得自增主键");
        }
        return key.longValue();
    }

    /** 按 id 查询，不存在返回 null */
    public Product findById(Long id) {
        List<Product> list = jdbcTemplate.query(SELECT_BY_ID_SQL, ROW_MAPPER, id);
        return list.isEmpty() ? null : list.get(0);
    }

    /** L0-5：更新商品上下架状态 */
    public void updateStatus(Long id, String status) {
        jdbcTemplate.update("UPDATE products SET status = ? WHERE id = ?", status, id);
    }

    // ===== L0-3 新增：列表查询 =====

    public long count(String keyword, String status, String category) {
        WhereClause clause = buildWhere(keyword, status, category);
        String sql = "SELECT COUNT(*) FROM products" + clause.sql;
        Long total = jdbcTemplate.queryForObject(sql, Long.class, clause.params.toArray());
        return total == null ? 0L : total;
    }

    public List<Product> findPage(String keyword, String status, String category, int offset, int limit) {
        WhereClause clause = buildWhere(keyword, status, category);
        String sql = "SELECT id, title, price, stock, category, description, status, seller_name, created_at"
                + " FROM products" + clause.sql + " ORDER BY id ASC LIMIT ? OFFSET ?";
        List<Object> params = new ArrayList<>(clause.params);
        params.add(limit);
        params.add(offset);
        return jdbcTemplate.query(sql, ROW_MAPPER, params.toArray());
    }

    private WhereClause buildWhere(String keyword, String status, String category) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null) {
            sql.append(" AND title LIKE ?");
            params.add("%" + keyword + "%");
        }
        if (status != null) {
            sql.append(" AND status = ?");
            params.add(status);
        }
        if (category != null) {
            sql.append(" AND category = ?");
            params.add(category);
        }
        return new WhereClause(sql.toString(), params);
    }

    private static final class WhereClause {
        private final String sql;
        private final List<Object> params;

        private WhereClause(String sql, List<Object> params) {
            this.sql = sql;
            this.params = params;
        }
    }
}
